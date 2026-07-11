package com.omnichannel.channels.whatsapp;

import java.util.Map;

import com.omnichannel.channels.ChannelAdapter;
import com.omnichannel.channels.ChannelCredentials;
import com.omnichannel.channels.ChannelDeliveryException;
import com.omnichannel.channels.ChannelType;
import com.omnichannel.channels.MessageKind;
import com.omnichannel.channels.OutboundMessage;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Envio pelo WhatsApp em dois modos, decididos pelas credenciais do canal:
 * <ul>
 *   <li><b>Cloud API (Meta for Developers)</b> — modo padrão para testes e
 *       produção direta com a Meta: {@code phone_number_id} +
 *       {@code access_token} (Bearer).</li>
 *   <li><b>BSP 360dialog</b> — {@code api_key} (header D360-API-KEY).</li>
 * </ul>
 * {@code base_url} opcional em ambos (testes apontam para um stub local).
 */
@Component
class WhatsAppChannelAdapter implements ChannelAdapter {

	static final String CLOUD_BASE_URL = "https://graph.facebook.com/v21.0";
	static final String DIALOG360_BASE_URL = "https://waba-v2.360dialog.io";

	private final RestClient restClient = RestClient.create();

	@Override
	public ChannelType type() {
		return ChannelType.WHATSAPP;
	}

	@Override
	public void send(OutboundMessage message, ChannelCredentials credentials) {
		if (message.content().kind() != MessageKind.TEXT) {
			throw new ChannelDeliveryException("WhatsApp: apenas texto no MVP (mídia na Fase 2)");
		}
		Map<String, Object> body = Map.of(
				"messaging_product", "whatsapp",
				"recipient_type", "individual",
				"to", message.externalContactId(),
				"type", "text",
				"text", Map.of("body", message.content().text()));
		try {
			if (credentials.values().containsKey("phone_number_id")) {
				sendViaCloudApi(body, credentials);
			}
			else {
				sendVia360dialog(body, credentials);
			}
		}
		catch (Exception e) {
			throw new ChannelDeliveryException("Falha ao enviar WhatsApp: " + e.getMessage(), e);
		}
	}

	private void sendViaCloudApi(Map<String, Object> body, ChannelCredentials credentials) {
		String baseUrl = credentials.values().getOrDefault("base_url", CLOUD_BASE_URL);
		restClient.post()
			.uri(baseUrl + "/" + credentials.require("phone_number_id") + "/messages")
			.header("Authorization", "Bearer " + credentials.require("access_token"))
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.retrieve()
			.toBodilessEntity();
	}

	private void sendVia360dialog(Map<String, Object> body, ChannelCredentials credentials) {
		String baseUrl = credentials.values().getOrDefault("base_url", DIALOG360_BASE_URL);
		restClient.post()
			.uri(baseUrl + "/messages")
			.header("D360-API-KEY", credentials.require("api_key"))
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.retrieve()
			.toBodilessEntity();
	}
}
