package com.omnichannel.tenant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import com.omnichannel.shared.ratelimit.RateLimitExceededException;
import com.omnichannel.shared.ratelimit.RateLimiter;
import com.omnichannel.shared.security.JwtService;
import com.omnichannel.shared.security.RateLimitProperties;
import com.omnichannel.shared.security.SecurityProperties;
import com.omnichannel.shared.tenancy.TenantContext;
import com.omnichannel.tenant.domain.RefreshToken;
import com.omnichannel.tenant.domain.RefreshTokenRepository;
import com.omnichannel.tenant.domain.Tenant;
import com.omnichannel.tenant.domain.TenantRepository;
import com.omnichannel.tenant.domain.User;
import com.omnichannel.tenant.domain.UserRepository;
import com.omnichannel.tenant.domain.UserRole;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Signup, login e refresh. Todo o fluxo roda em
 * {@link TenantContext#runAsAuthProcess}: é o único ponto do sistema
 * autorizado a consultar usuários por e-mail sem tenant na sessão
 * (política auth_context da migration V3). A transação é aberta por
 * {@link TransactionTemplate} DENTRO do modo auth — com @Transactional a
 * conexão seria preparada antes de o contexto existir.
 */
@Service
public class AuthService {

	private static final int REFRESH_TOKEN_BYTES = 48;

	/**
	 * Hash Argon2id de uma senha aleatória, usado quando o e-mail não
	 * existe: o tempo de resposta fica igual ao de senha errada, sem
	 * revelar quais e-mails estão cadastrados.
	 */
	private final String unknownUserDummyHash;

	private final TenantRepository tenantRepository;
	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final SecurityProperties securityProperties;
	private final RateLimiter rateLimiter;
	private final RateLimitProperties rateLimitProperties;
	private final TwoFactorService twoFactorService;
	private final TransactionTemplate transactionTemplate;
	private final SecureRandom secureRandom = new SecureRandom();

	public AuthService(TenantRepository tenantRepository, UserRepository userRepository,
			RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
			SecurityProperties securityProperties, RateLimiter rateLimiter, RateLimitProperties rateLimitProperties,
			TwoFactorService twoFactorService, PlatformTransactionManager transactionManager) {
		this.tenantRepository = tenantRepository;
		this.userRepository = userRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.securityProperties = securityProperties;
		this.rateLimiter = rateLimiter;
		this.rateLimitProperties = rateLimitProperties;
		this.twoFactorService = twoFactorService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.unknownUserDummyHash = passwordEncoder.encode(generateRefreshTokenValue());
	}

	public AuthTokens signup(String tenantName, String userName, String email, String rawPassword) {
		String normalizedEmail = User.normalizeEmail(email);
		return TenantContext.runAsAuthProcess(() -> transactionTemplate.execute(status -> {
			if (userRepository.existsByEmail(normalizedEmail)) {
				throw new EmailAlreadyRegisteredException();
			}
			Tenant tenant = tenantRepository.save(new Tenant(tenantName));
			User admin = userRepository.save(new User(tenant.getId(), userName, normalizedEmail,
					passwordEncoder.encode(rawPassword), UserRole.ADMIN));
			return issueTokens(admin);
		}));
	}

	public LoginResult login(String email, String rawPassword) {
		String normalizedEmail = User.normalizeEmail(email);
		// Limite por conta-alvo: só FALHAS contam e o sucesso zera — proteção
		// contra força bruta sem permitir que um atacante tranque a vítima
		// para sempre (docs/06-seguranca.md).
		String failureKey = "rl:auth:fail:" + sha256(normalizedEmail);
		if (rateLimiter.isBlocked(failureKey, rateLimitProperties.emailFailureLimit())) {
			throw new RateLimitExceededException("Muitas tentativas para esta conta. Tente novamente mais tarde.",
					rateLimiter.secondsUntilReset(failureKey, rateLimitProperties.emailFailureWindow()));
		}
		LoginResult result = TenantContext.runAsAuthProcess(() -> transactionTemplate.execute(status -> {
			User user = userRepository.findByEmail(normalizedEmail).orElse(null);
			if (user == null) {
				passwordEncoder.matches(rawPassword, unknownUserDummyHash);
				return null;
			}
			if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
				return null;
			}
			if (user.isTotpEnabled()) {
				// senha certa, mas tokens só depois do código 2FA
				return LoginResult.mfaChallenge(jwtService.generateMfaChallengeToken(user.getId()));
			}
			return LoginResult.authenticated(issueTokens(user));
		}));
		if (result == null) {
			rateLimiter.record(failureKey, rateLimitProperties.emailFailureWindow());
			throw new AuthenticationFailedException("Credenciais inválidas");
		}
		rateLimiter.reset(failureKey);
		return result;
	}

	/** Segunda etapa do login com 2FA: troca desafio + código por tokens. */
	public AuthTokens verifyMfaChallenge(String mfaToken, String code) {
		java.util.UUID userId = twoFactorService.challengeTokenUserId(mfaToken);
		// código de 6 dígitos é forçável — mesmo regime de bloqueio do login
		String failureKey = "rl:auth:mfa:" + userId;
		if (rateLimiter.isBlocked(failureKey, rateLimitProperties.emailFailureLimit())) {
			throw new RateLimitExceededException("Muitas tentativas de código. Tente novamente mais tarde.",
					rateLimiter.secondsUntilReset(failureKey, rateLimitProperties.emailFailureWindow()));
		}
		AuthTokens tokens = TenantContext.runAsAuthProcess(() -> transactionTemplate.execute(status -> {
			User user = userRepository.findById(userId).orElse(null);
			if (user == null || !user.isTotpEnabled()
					|| !twoFactorService.verifyTotpOrRecoveryCode(user, code)) {
				return null;
			}
			return issueTokens(user);
		}));
		if (tokens == null) {
			rateLimiter.record(failureKey, rateLimitProperties.emailFailureWindow());
			throw new AuthenticationFailedException("Código 2FA inválido");
		}
		rateLimiter.reset(failureKey);
		return tokens;
	}

	/**
	 * Enrolamento de 2FA — rotas autenticadas: o contexto de tenant já foi
	 * definido pelo interceptor, então @Transactional é seguro aqui.
	 */
	@org.springframework.transaction.annotation.Transactional
	public TwoFactorService.TotpEnrollment startTotpEnrollment(java.util.UUID userId) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new AuthenticationFailedException("Usuário não encontrado"));
		return twoFactorService.startEnrollment(user);
	}

	@org.springframework.transaction.annotation.Transactional
	public java.util.List<String> confirmTotpEnrollment(java.util.UUID userId, String code) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new AuthenticationFailedException("Usuário não encontrado"));
		return twoFactorService.confirmEnrollment(user, code);
	}

	public AuthTokens refresh(String refreshTokenValue) {
		return TenantContext.runAsAuthProcess(() -> transactionTemplate.execute(status -> {
			RefreshToken current = refreshTokenRepository.findByTokenHash(sha256(refreshTokenValue))
				.filter(RefreshToken::isUsable)
				.orElseThrow(() -> new AuthenticationFailedException("Refresh token inválido ou expirado"));
			current.revoke();
			User user = userRepository.findById(current.getUserId())
				.orElseThrow(() -> new AuthenticationFailedException("Usuário do token não existe mais"));
			return issueTokens(user);
		}));
	}

	private AuthTokens issueTokens(User user) {
		String accessToken = jwtService.generateAccessToken(user.getId(), user.getTenantId(),
				user.getRole().name(), user.getEmail());
		String refreshTokenValue = generateRefreshTokenValue();
		Instant expiresAt = Instant.now().plus(securityProperties.refreshTokenTtl());
		refreshTokenRepository
			.save(new RefreshToken(user.getTenantId(), user.getId(), sha256(refreshTokenValue), expiresAt));
		return new AuthTokens(accessToken, refreshTokenValue,
				securityProperties.accessTokenTtl().toSeconds());
	}

	private String generateRefreshTokenValue() {
		byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 indisponível na JVM", e);
		}
	}
}
