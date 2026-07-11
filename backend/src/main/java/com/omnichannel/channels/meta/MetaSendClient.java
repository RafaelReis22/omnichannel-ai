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

	void send(OutboundMessage message, ChannelCredentials credentials, String channelLabel) {
		var content = message.content();
		try {
			if (content.kind() == MessageKind.TEXT) {
				post(message.externalContactId(), Map.of("text", content.text()), credentials);
				return;
			}
			String type = switch (content.kind()) {
				case IMAGE -> "image";
				case VIDEO -> "video";
				case AUDIO -> "audio";
				default -> "file";
			};
			post(message.externalContactId(), Map.of("attachment",
					Map.of("type", type, "payload", Map.of("url", content.mediaUrl(), "is_reusable", false))),
					credentials);
			// a Messenger Platform não aceita caption no attachment — a
			// legenda segue como mensagem de texto na sequência
			if (content.text() != null && !content.text().isBlank()) {
				post(message.externalContactId(), Map.of("text", content.text()), credentials);
			}
		}
		catch (Exception e) {
			throw new ChannelDeliveryException("Falha ao enviar " + channelLabel + ": " + e.getMessage(), e);
		}
	}

	private void post(String recipientId, Map<String, Object> messagePayload, ChannelCredentials credentials) {
		String baseUrl = credentials.values().getOrDefault("base_url", DEFAULT_BASE_URL);
		restClient.post()
			.uri(baseUrl + "/me/messages?access_token={token}", credentials.require("access_token"))
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("recipient", Map.of("id", recipientId), "message", messagePayload))
			.retrieve()
			.toBodilessEntity();
	}
}
