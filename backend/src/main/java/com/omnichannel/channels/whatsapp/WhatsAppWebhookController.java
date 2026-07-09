package com.omnichannel.channels.whatsapp;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.omnichannel.channels.ChannelDirectory;
import com.omnichannel.channels.ChannelType;
import com.omnichannel.channels.InboundMessageGateway;
import com.omnichannel.channels.InboundMessageReceived;
import com.omnichannel.channels.MessageContent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook de entrada do WhatsApp (payload no formato Cloud API da Meta,
 * encaminhado pelo BSP). A 360dialog não assina requisições — a
 * autenticação é o {@code webhookToken} secreto por canal, embutido na URL
 * configurada no painel do BSP (docs/06-seguranca.md: verificação de
 * origem em todo webhook).
 *
 * <p>Responde 200 mesmo para eventos ignorados (status de entrega etc.):
 * o BSP reenvia em caso de erro, e reentrega de mensagem é inofensiva
 * (dedup por id no inbox).
 */
@RestController
class WhatsAppWebhookController {

	private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

	private final ChannelDirectory channelDirectory;
	private final InboundMessageGateway gateway;

	WhatsAppWebhookController(ChannelDirectory channelDirectory, InboundMessageGateway gateway) {
		this.channelDirectory = channelDirectory;
		this.gateway = gateway;
	}

	@PostMapping("/api/whatsapp/webhook/{channelId}/{webhookToken}")
	ResponseEntity<Void> receive(@PathVariable UUID channelId, @PathVariable String webhookToken,
			@RequestBody Map<String, Object> payload) {
		var channel = channelDirectory.findActiveForWebhook(channelId, ChannelType.WHATSAPP, webhookToken)
			.orElse(null);
		if (channel == null) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		for (ParsedMessage parsed : parse(payload)) {
			gateway.receive(new InboundMessageReceived(channel.tenantId(), channel.channelId(),
					ChannelType.WHATSAPP, parsed.from(), parsed.profileName(), parsed.messageId(),
					MessageContent.text(parsed.text()), Instant.now()));
		}
		return ResponseEntity.ok().build();
	}

	record ParsedMessage(String from, String profileName, String messageId, String text) {
	}

	/** Extrai mensagens de texto do envelope Cloud API; ignora o resto (status etc.). */
	@SuppressWarnings("unchecked")
	private List<ParsedMessage> parse(Map<String, Object> payload) {
		try {
			var result = new java.util.ArrayList<ParsedMessage>();
			for (Map<String, Object> entry : (List<Map<String, Object>>) payload.getOrDefault("entry", List.of())) {
				for (Map<String, Object> change : (List<Map<String, Object>>) entry.getOrDefault("changes",
						List.of())) {
					Map<String, Object> value = (Map<String, Object>) change.getOrDefault("value", Map.of());
					String profileName = extractProfileName(value);
					for (Map<String, Object> message : (List<Map<String, Object>>) value.getOrDefault("messages",
							List.of())) {
						if (!"text".equals(message.get("type"))) {
							continue; // mídia entra na Fase 2
						}
						Map<String, Object> text = (Map<String, Object>) message.get("text");
						result.add(new ParsedMessage((String) message.get("from"), profileName,
								(String) message.get("id"), (String) text.get("body")));
					}
				}
			}
			return result;
		}
		catch (ClassCastException e) {
			log.warn("Payload de webhook WhatsApp fora do formato esperado: {}", e.getMessage());
			return List.of();
		}
	}

	@SuppressWarnings("unchecked")
	private String extractProfileName(Map<String, Object> value) {
		List<Map<String, Object>> contacts = (List<Map<String, Object>>) value.getOrDefault("contacts", List.of());
		if (contacts.isEmpty()) {
			return null;
		}
		Map<String, Object> profile = (Map<String, Object>) contacts.getFirst().getOrDefault("profile", Map.of());
		return (String) profile.get("name");
	}
}
