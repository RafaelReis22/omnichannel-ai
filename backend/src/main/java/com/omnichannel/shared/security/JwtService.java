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

	private static final String ISSUER = "omnichannel-ai";

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
}
