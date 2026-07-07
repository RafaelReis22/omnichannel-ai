/**
 * Módulo de analytics: métricas operacionais (volume, tempo de resposta,
 * taxa de resolução por canal). Consome eventos de domínio dos demais
 * módulos — nunca é chamado diretamente por eles (CQRS leve).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Analytics")
package com.omnichannel.analytics;
