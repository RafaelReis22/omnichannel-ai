package com.omnichannel.channels;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de domínio: uma mensagem chegou por um canal e foi normalizada.
 * Publicado pelo {@link InboundMessageGateway} via outbox do Modulith —
 * inbox e IA consomem este evento, nunca o payload bruto do provedor.
 *
 * @param externalContactId identidade do contato no canal de origem
 *                          (telefone no WhatsApp, PSID no Messenger...)
 * @param externalMessageId id da mensagem no provedor, para deduplicação
 */
public record InboundMessageReceived(
		UUID tenantId,
		UUID channelId,
		ChannelType channelType,
		String externalContactId,
		String externalMessageId,
		MessageContent content,
		Instant receivedAt) {
}
