package com.omnichannel.shared.security;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;

/**
 * Emissão de access tokens. Claims mínimas: {@code sub} (id do usuário),
 * {@code tenant_id}, {@code role} e {@code email} — tudo que o resto da
 * aplicação precisa para autorizar e escopar por tenant sem ir ao banco.
 */
@Service
public class JwtService {

	public static final String CLAIM_TENANT_ID = "tenant_id";
	public static final String CLAIM_ROLE = "role";
	public static final String CLAIM_EMAIL = "email";
	public static final String CLAIM_MFA = "mfa";
	public static final String MFA_PENDING = "pending";

	private static final String ISSUER = "omnichannel-ai";
	private static final java.time.Duration MFA_TOKEN_TTL = java.time.Duration.ofMinutes(5);

	private final JwtEncoder jwtEncoder;
	private final SecurityProperties properties;

	public JwtService(JwtEncoder jwtEncoder, SecurityProperties properties) {
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
	}

	public String generateAccessToken(UUID userId, UUID tenantId, String role, String email) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(ISSUER)
			.subject(userId.toString())
			.issuedAt(now)
			.expiresAt(now.plus(properties.accessTokenTtl()))
			.claim(CLAIM_TENANT_ID, tenantId.toString())
			.claim(CLAIM_ROLE, role)
			.claim(CLAIM_EMAIL, email)
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	/**
	 * Token efêmero da etapa intermediária do login com 2FA: prova que a
	 * senha foi validada, mas NÃO autentica nenhuma rota (o resource server
	 * rejeita pela claim {@code mfa=pending} — ver SecurityConfig).
	 */
	public String generateMfaChallengeToken(UUID userId) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(ISSUER)
			.subject(userId.toString())
			.issuedAt(now)
			.expiresAt(now.plus(MFA_TOKEN_TTL))
			.claim(CLAIM_MFA, MFA_PENDING)
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}
}
