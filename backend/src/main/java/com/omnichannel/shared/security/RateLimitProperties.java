package com.omnichannel.shared.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limites de força bruta em autenticação (docs/06-seguranca.md).
 *
 * @param ipLimit            requisições em /api/auth/** por IP por janela
 * @param ipWindow           janela do limite por IP
 * @param emailFailureLimit  falhas de login por conta-alvo por janela
 *                           (só falhas contam; sucesso zera o contador)
 * @param emailFailureWindow janela do limite por conta
 */
@ConfigurationProperties(prefix = "app.security.rate-limit")
public record RateLimitProperties(int ipLimit, Duration ipWindow, int emailFailureLimit,
		Duration emailFailureWindow) {
}
