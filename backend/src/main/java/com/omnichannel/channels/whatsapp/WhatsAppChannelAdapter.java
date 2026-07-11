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
		Map<String, Object> body = buildPayload(message);
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

	/**
	 * Nono dígito brasileiro: o wa_id que a Meta entrega vem SEM o 9 de
	 * celulares antigos (55 + DDD + 8 dígitos), mas o número real — e a
	 * lista de destinatários permitidos do número de teste — têm o 9.
	 * Recolocamos ao enviar; números já com 13 dígitos passam intactos.
	 */
	static String normalizeBrazilianMobile(String to) {
		if (to != null && to.startsWith("55") && to.length() == 12) {
			char firstDigit = to.charAt(4);
			if (firstDigit >= '6' && firstDigit <= '9') {
				return to.substring(0, 4) + "9" + to.substring(4);
			}
		}
		return to;
	}

	/** Formato Cloud API, comum aos dois provedores; mídia vai por link (URL pré-assinada). */
	private Map<String, Object> buildPayload(OutboundMessage message) {
		var content = message.content();
		var body = new java.util.HashMap<String, Object>();
		body.put("messaging_product", "whatsapp");
		body.put("recipient_type", "individual");
		body.put("to", normalizeBrazilianMobile(message.externalContactId()));
		if (content.kind() == MessageKind.TEXT) {
			body.put("type", "text");
			body.put("text", Map.of("body", content.text()));
			return body;
		}
		String field = switch (content.kind()) {
			case IMAGE -> "image";
			case VIDEO -> "video";
			case AUDIO -> "audio";
			default -> "document";
		};
		var media = new java.util.HashMap<String, Object>();
		media.put("link", content.mediaUrl());
		// áudio não aceita caption na Cloud API
		if (content.text() != null && !content.text().isBlank() && content.kind() != MessageKind.AUDIO) {
			media.put("caption", content.text());
		}
		body.put("type", field);
		body.put(field, media);
		return body;
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
