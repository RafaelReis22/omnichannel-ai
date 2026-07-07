package com.omnichannel.shared.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração de autenticação ({@code app.security.*} no application.yml,
 * alimentada pelas variáveis JWT_* do .env).
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(String jwtSecret, Duration accessTokenTtl, Duration refreshTokenTtl) {

	private static final int MIN_SECRET_LENGTH = 32;

	public SecurityProperties {
		if (jwtSecret == null || jwtSecret.length() < MIN_SECRET_LENGTH) {
			throw new IllegalStateException(
					"JWT_SECRET ausente ou curto demais (mínimo %d caracteres). Gere com: openssl rand -base64 64"
						.formatted(MIN_SECRET_LENGTH));
		}
	}
}
