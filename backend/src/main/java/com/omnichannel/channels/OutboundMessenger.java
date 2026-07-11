package com.omnichannel.channels;

import com.omnichannel.channels.domain.Channel;
import com.omnichannel.channels.domain.ChannelService;
import com.omnichannel.shared.storage.StorageService;

import org.springframework.stereotype.Component;

/**
 * Porta de saída única de mensagens: resolve o adapter e as credenciais do
 * canal internamente — nenhum outro módulo toca credenciais
 * (docs/06-seguranca.md). Roda no contexto de tenant do chamador.
 *
 * <p>Mídia: a referência interna {@code storage://} vira URL pré-assinada
 * AQUI, uma única vez — todo adapter recebe uma URL https que o provedor
 * externo consegue baixar dentro do TTL.
 */
@Component
public class OutboundMessenger {

	private final ChannelService channelService;
	private final ChannelRegistry channelRegistry;
	private final StorageService storageService;

	public OutboundMessenger(ChannelService channelService, ChannelRegistry channelRegistry,
			StorageService storageService) {
		this.channelService = channelService;
		this.channelRegistry = channelRegistry;
		this.storageService = storageService;
	}

	public void send(OutboundMessage message) {
		Channel channel = channelService.requireActiveChannelForSend(message.channelId());
		ChannelAdapter adapter = channelRegistry.adapterFor(channel.getType());
		adapter.send(withResolvedMedia(message), channelService.credentialsOf(channel.getId()));
	}

	private OutboundMessage withResolvedMedia(OutboundMessage message) {
		MessageContent content = message.content();
		if (content.mediaUrl() == null) {
			return message;
		}
		MessageContent resolved = new MessageContent(content.kind(), content.text(),
				storageService.resolveUrl(content.mediaUrl()), content.mimeType());
		return new OutboundMessage(message.channelId(), message.externalContactId(), resolved);
	}
}
