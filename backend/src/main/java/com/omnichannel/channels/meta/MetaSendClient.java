package com.omnichannel.channels.meta;

import java.util.Map;

import com.omnichannel.channels.ChannelCredentials;
import com.omnichannel.channels.ChannelDeliveryException;
import com.omnichannel.channels.MessageKind;
import com.omnichannel.channels.OutboundMessage;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Envio comum aos canais Meta (Messenger e Instagram): Graph API
 * {@code POST /me/messages} com o {@code access_token} da página conectada.
 * Credenciais: {@code access_token} e, opcionalmente, {@code base_url}
 * (padrão produção; testes apontam para um stub local).
 */
@Component
class MetaSendClient {

	static final String DEFAULT_BASE_URL = "https://graph.facebook.com/v21.0";

	private final RestClient restClient = RestClient.create();

	void sendText(OutboundMessage message, ChannelCredentials credentials, String channelLabel) {
		if (message.content().kind() != MessageKind.TEXT) {
			throw new ChannelDeliveryException(channelLabel + ": apenas texto no MVP (mídia na Fase 2)");
		}
		String baseUrl = credentials.values().getOrDefault("base_url", DEFAULT_BASE_URL);
		try {
			restClient.post()
				.uri(baseUrl + "/me/messages?access_token={token}", credentials.require("access_token"))
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of(
						"recipient", Map.of("id", message.externalContactId()),
						"message", Map.of("text", message.content().text())))
				.retrieve()
				.toBodilessEntity();
		}
		catch (Exception e) {
			throw new ChannelDeliveryException("Falha ao enviar " + channelLabel + ": " + e.getMessage(), e);
		}
	}
}
