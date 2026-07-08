package com.omnichannel.channels;

import com.omnichannel.channels.domain.Channel;
import com.omnichannel.channels.domain.ChannelService;

import org.springframework.stereotype.Component;

/**
 * Porta de saída única de mensagens: resolve o adapter e as credenciais do
 * canal internamente — nenhum outro módulo toca credenciais
 * (docs/06-seguranca.md). Roda no contexto de tenant do chamador.
 */
@Component
public class OutboundMessenger {

	private final ChannelService channelService;
	private final ChannelRegistry channelRegistry;

	public OutboundMessenger(ChannelService channelService, ChannelRegistry channelRegistry) {
		this.channelService = channelService;
		this.channelRegistry = channelRegistry;
	}

	public void send(OutboundMessage message) {
		Channel channel = channelService.requireActiveChannelForSend(message.channelId());
		ChannelAdapter adapter = channelRegistry.adapterFor(channel.getType());
		adapter.send(message, channelService.credentialsOf(channel.getId()));
	}
}
