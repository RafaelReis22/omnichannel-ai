package com.omnichannel.inbox;

import java.util.UUID;

/**
 * Evento leve de "algo mudou nesta conversa" — alimenta o push em tempo
 * real do dashboard. Publicado em toda mutação de conversa (mensagem nova
 * de qualquer remetente, atribuição, resolução).
 */
public record ConversationActivity(UUID tenantId, UUID conversationId) {
}
