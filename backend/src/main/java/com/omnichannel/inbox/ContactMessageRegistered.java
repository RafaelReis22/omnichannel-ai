package com.omnichannel.inbox;

import java.util.UUID;

/**
 * Evento de domínio: uma mensagem DE CONTATO foi materializada no inbox.
 * É o gatilho do AI Worker — publicado depois da materialização para que
 * o consumidor já encontre a conversa e o histórico completos.
 */
public record ContactMessageRegistered(
		UUID tenantId,
		UUID conversationId,
		UUID messageId,
		String text) {
}
