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
 * Envio pelo WhatsApp via BSP 360dialog (docs/04-stack-tecnologica.md).
 * Credenciais esperadas: {@code api_key} (D360-API-KEY) e, opcionalmente,
 * {@code base_url} (padrão produção; nos testes aponta para um stub local).
 */
@Component
class WhatsAppChannelAdapter implements ChannelAdapter {

	static final String DEFAULT_BASE_URL = "https://waba-v2.360dialog.io";

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
		String baseUrl = credentials.values().getOrDefault("base_url", DEFAULT_BASE_URL);
		try {
			restClient.post()
				.uri(baseUrl + "/messages")
				.header("D360-API-KEY", credentials.require("api_key"))
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of(
						"messaging_product", "whatsapp",
						"recipient_type", "individual",
						"to", message.externalContactId(),
						"type", "text",
						"text", Map.of("body", message.content().text())))
				.retrieve()
				.toBodilessEntity();
		}
		catch (Exception e) {
			throw new ChannelDeliveryException("Falha ao enviar WhatsApp: " + e.getMessage(), e);
		}
	}
}
