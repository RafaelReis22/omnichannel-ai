package com.omnichannel.channels.meta;

import com.omnichannel.channels.ChannelAdapter;
import com.omnichannel.channels.ChannelCredentials;
import com.omnichannel.channels.ChannelType;
import com.omnichannel.channels.OutboundMessage;

import org.springframework.stereotype.Component;

/** Facebook Messenger via Graph API (docs/04-stack-tecnologica.md). */
@Component
class MessengerChannelAdapter implements ChannelAdapter {

	private final MetaSendClient sendClient;

	MessengerChannelAdapter(MetaSendClient sendClient) {
		this.sendClient = sendClient;
	}

	@Override
	public ChannelType type() {
		return ChannelType.MESSENGER;
	}

	@Override
	public void send(OutboundMessage message, ChannelCredentials credentials) {
		sendClient.send(message, credentials, "Messenger");
	}
}
