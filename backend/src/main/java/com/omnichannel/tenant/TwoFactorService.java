package com.omnichannel.tenant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.omnichannel.shared.security.AesGcmCipher;
import com.omnichannel.shared.security.JwtService;
import com.omnichannel.shared.security.SecurityProperties;
import com.omnichannel.tenant.domain.RecoveryCode;
import com.omnichannel.tenant.domain.RecoveryCodeRepository;
import com.omnichannel.tenant.domain.User;

import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

import javax.crypto.spec.SecretKeySpec;

/**
 * Primitivas de 2FA TOTP (RFC 6238): enrolamento, verificação de código e
 * códigos de recuperação. O fluxo de login em duas etapas fica no
 * {@link AuthService}, que usa estas primitivas.
 */
@Service
public class TwoFactorService {

	private static final String ISSUER = "omnichannel-ai";
	private static final int RECOVERY_CODE_COUNT = 8;

	private final AesGcmCipher totpCipher;
	private final RecoveryCodeRepository recoveryCodeRepository;
	private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
	private final CodeVerifier codeVerifier;
	/**
	 * Decoder próprio para o token de desafio: o decoder do resource server
	 * REJEITA tokens mfa=pending de propósito (ver SecurityConfig), então a
	 * troca desafio→tokens precisa de um caminho de validação separado.
	 */
	private final JwtDecoder challengeDecoder;
	private final SecureRandom secureRandom = new SecureRandom();

	public TwoFactorService(@Value("${app.security.totp-encryption-key:}") String totpKey,
			SecurityProperties securityProperties, RecoveryCodeRepository recoveryCodeRepository) {
		this.totpCipher = new AesGcmCipher(totpKey, "TOTP_ENCRYPTION_KEY");
		this.recoveryCodeRepository = recoveryCodeRepository;
		DefaultCodeVerifier verifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
		verifier.setAllowedTimePeriodDiscrepancy(1);
		this.codeVerifier = verifier;
		this.challengeDecoder = NimbusJwtDecoder
			.withSecretKey(new SecretKeySpec(securityProperties.jwtSecret().getBytes(StandardCharsets.UTF_8),
					"HmacSHA256"))
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
	}

	public record TotpEnrollment(String secret, String otpauthUri) {
	}

	/** Gera segredo e URI otpauth:// para o QR; o 2FA só ativa no confirm. */
	public TotpEnrollment startEnrollment(User user) {
		String secret = secretGenerator.generate();
		user.startTotpEnrollment(totpCipher.encrypt(secret));
		QrData qrData = new QrData.Builder()
			.label(user.getEmail())
			.secret(secret)
			.issuer(ISSUER)
			.algorithm(HashingAlgorithm.SHA1)
			.digits(6)
			.period(30)
			.build();
		return new TotpEnrollment(secret, qrData.getUri());
	}

	/** Confirma o enrolamento e devolve os códigos de recuperação (única exibição). */
	public List<String> confirmEnrollment(User user, String code) {
		if (!verifyTotp(user, code)) {
			throw new AuthenticationFailedException("Código 2FA inválido");
		}
		user.confirmTotpEnrollment();
		recoveryCodeRepository.deleteAll(recoveryCodeRepository.findAllByUserId(user.getId()));
		List<String> plainCodes = new ArrayList<>(RECOVERY_CODE_COUNT);
		for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
			String plain = generateRecoveryCode();
			plainCodes.add(plain);
			recoveryCodeRepository.save(new RecoveryCode(user.getTenantId(), user.getId(), sha256(plain)));
		}
		return plainCodes;
	}

	/** TOTP válido, ou código de recuperação não usado (que é consumido). */
	public boolean verifyTotpOrRecoveryCode(User user, String code) {
		if (verifyTotp(user, code)) {
			return true;
		}
		return recoveryCodeRepository.findByUserIdAndCodeHash(user.getId(), sha256(code.trim().toUpperCase()))
			.filter(RecoveryCode::isUsable)
			.map(recoveryCode -> {
				recoveryCode.markUsed();
				return true;
			})
			.orElse(false);
	}

	/** Valida o token de desafio e devolve o usuário dono, ou falha com 401. */
	public UUID challengeTokenUserId(String mfaToken) {
		try {
			Jwt jwt = challengeDecoder.decode(mfaToken);
			if (!JwtService.MFA_PENDING.equals(jwt.getClaimAsString(JwtService.CLAIM_MFA))) {
				throw new AuthenticationFailedException("Token de desafio MFA inválido");
			}
			return UUID.fromString(jwt.getSubject());
		}
		catch (JwtException e) {
			throw new AuthenticationFailedException("Token de desafio MFA inválido ou expirado");
		}
	}

	private boolean verifyTotp(User user, String code) {
		if (user.getTotpSecretEncrypted() == null) {
			return false;
		}
		return codeVerifier.isValidCode(totpCipher.decrypt(user.getTotpSecretEncrypted()), code.trim());
	}

	private String generateRecoveryCode() {
		// 10 caracteres legíveis (sem 0/O/1/I), formato XXXXX-XXXXX
		String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
		StringBuilder builder = new StringBuilder(11);
		for (int i = 0; i < 10; i++) {
			if (i == 5) {
				builder.append('-');
			}
			builder.append(alphabet.charAt(secureRandom.nextInt(alphabet.length())));
		}
		return builder.toString();
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
