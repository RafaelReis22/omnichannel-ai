package com.omnichannel.channels.webchat;

import com.omnichannel.channels.ChannelAdapter;
import com.omnichannel.channels.ChannelCredentials;
import com.omnichannel.channels.ChannelType;
import com.omnichannel.channels.OutboundMessage;

import org.springframework.stereotype.Component;

/**
 * Webchat é o nosso próprio canal: não há provedor externo para entregar.
 * A mensagem do agente/IA já está persistida no inbox quando chega aqui, e
 * o widget a recebe via polling do endpoint público — portanto o envio é,
 * corretamente, um no-op. Quando o tempo real chegar (WebSocket, Fase 2),
 * este adapter passa a publicar no socket do visitante.
 */
@Component
class WebchatChannelAdapter implements ChannelAdapter {

	@Override
	public ChannelType type() {
		return ChannelType.WEBCHAT;
	}

	@Override
	public void send(OutboundMessage message, ChannelCredentials credentials) {
		// entrega = persistência no inbox + polling do widget
	}
}
