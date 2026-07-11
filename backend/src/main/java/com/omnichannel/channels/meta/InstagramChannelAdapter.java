package com.omnichannel.channels.meta;

import com.omnichannel.channels.ChannelAdapter;
import com.omnichannel.channels.ChannelCredentials;
import com.omnichannel.channels.ChannelType;
import com.omnichannel.channels.OutboundMessage;

import org.springframework.stereotype.Component;

/** Instagram Direct via Messenger Platform (docs/04-stack-tecnologica.md). */
@Component
class InstagramChannelAdapter implements ChannelAdapter {

	private final MetaSendClient sendClient;

	InstagramChannelAdapter(MetaSendClient sendClient) {
		this.sendClient = sendClient;
	}

	@Override
	public ChannelType type() {
		return ChannelType.INSTAGRAM;
	}

	@Override
	public void send(OutboundMessage message, ChannelCredentials credentials) {
		sendClient.send(message, credentials, "Instagram");
	}
}
