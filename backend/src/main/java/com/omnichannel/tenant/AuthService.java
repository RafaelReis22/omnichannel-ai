package com.omnichannel.tenant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import com.omnichannel.shared.security.JwtService;
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
	private final TransactionTemplate transactionTemplate;
	private final SecureRandom secureRandom = new SecureRandom();

	public AuthService(TenantRepository tenantRepository, UserRepository userRepository,
			RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
			SecurityProperties securityProperties, PlatformTransactionManager transactionManager) {
		this.tenantRepository = tenantRepository;
		this.userRepository = userRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.securityProperties = securityProperties;
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

	public AuthTokens login(String email, String rawPassword) {
		String normalizedEmail = User.normalizeEmail(email);
		return TenantContext.runAsAuthProcess(() -> transactionTemplate.execute(status -> {
			User user = userRepository.findByEmail(normalizedEmail).orElse(null);
			if (user == null) {
				passwordEncoder.matches(rawPassword, unknownUserDummyHash);
				throw new AuthenticationFailedException("Credenciais inválidas");
			}
			if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
				throw new AuthenticationFailedException("Credenciais inválidas");
			}
			return issueTokens(user);
		}));
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
