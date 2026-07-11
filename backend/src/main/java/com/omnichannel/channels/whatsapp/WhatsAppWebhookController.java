package com.omnichannel.channels.whatsapp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnichannel.channels.ChannelDirectory;
import com.omnichannel.channels.ChannelType;
import com.omnichannel.channels.InboundMessageGateway;
import com.omnichannel.channels.InboundMessageReceived;
import com.omnichannel.channels.MessageContent;
import com.omnichannel.channels.meta.MetaSignatures;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook de entrada do WhatsApp — formato Cloud API da Meta, servindo os
 * dois provedores:
 * <ul>
 *   <li><b>Meta for Developers (Cloud API)</b>: GET de verificação
 *       ({@code hub.challenge}; o verify token É o token da URL) e POST
 *       assinado com {@code X-Hub-Signature-256} — assinatura EXIGIDA
 *       quando o canal tem {@code app_secret};</li>
 *   <li><b>360dialog</b>: POST sem assinatura — o token secreto na URL é a
 *       autenticação (docs/06-seguranca.md).</li>
 * </ul>
 * Responde 200 a eventos ignorados (status de entrega etc.): o provedor
 * reenvia em caso de erro, e reentrega de mensagem é inofensiva (dedup por
 * id no inbox).
 */
@RestController
class WhatsAppWebhookController {

	private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

	private final ChannelDirectory channelDirectory;
	private final InboundMessageGateway gateway;
	private final ObjectMapper objectMapper;
	private final WhatsAppMediaClient mediaClient;
	private final com.omnichannel.channels.domain.ChannelService channelService;
	private final com.omnichannel.shared.storage.StorageService storageService;

	WhatsAppWebhookController(ChannelDirectory channelDirectory, InboundMessageGateway gateway,
			ObjectMapper objectMapper, WhatsAppMediaClient mediaClient,
			com.omnichannel.channels.domain.ChannelService channelService,
			com.omnichannel.shared.storage.StorageService storageService) {
		this.channelDirectory = channelDirectory;
		this.gateway = gateway;
		this.objectMapper = objectMapper;
		this.mediaClient = mediaClient;
		this.channelService = channelService;
		this.storageService = storageService;
	}

	/** Verificação de endpoint da Meta: o verify token é o próprio token da URL. */
	@GetMapping("/api/whatsapp/webhook/{channelId}/{webhookToken}")
	ResponseEntity<String> verify(@PathVariable UUID channelId, @PathVariable String webhookToken,
			@RequestParam(name = "hub.mode", required = false) String mode,
			@RequestParam(name = "hub.verify_token", required = false) String verifyToken,
			@RequestParam(name = "hub.challenge", required = false) String challenge) {
		boolean channelOk = channelDirectory.findActiveForWebhook(channelId, ChannelType.WHATSAPP, webhookToken)
			.isPresent();
		if (!channelOk || !"subscribe".equals(mode) || verifyToken == null
				|| !MessageDigest.isEqual(webhookToken.getBytes(StandardCharsets.UTF_8),
						verifyToken.getBytes(StandardCharsets.UTF_8))) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		return ResponseEntity.ok(challenge);
	}

	@PostMapping("/api/whatsapp/webhook/{channelId}/{webhookToken}")
	ResponseEntity<Void> receive(@PathVariable UUID channelId, @PathVariable String webhookToken,
			@RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
			@RequestBody String rawBody) {
		var channel = channelDirectory.findActiveForWebhook(channelId, ChannelType.WHATSAPP, webhookToken)
			.orElse(null);
		if (channel == null) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		// canal com app_secret (Cloud API): assinatura da Meta é obrigatória
		String appSecret = channelDirectory.credentialOf(channelId, "app_secret").orElse(null);
		if (appSecret != null) {
			String expected = "sha256=" + MetaSignatures.hmacSha256(appSecret, rawBody);
			if (signature == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
					signature.getBytes(StandardCharsets.UTF_8))) {
				return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
			}
		}
		for (ParsedMessage parsed : parse(rawBody, channel)) {
			gateway.receive(new InboundMessageReceived(channel.tenantId(), channel.channelId(),
					ChannelType.WHATSAPP, parsed.from(), parsed.profileName(), parsed.messageId(),
					parsed.content(), Instant.now()));
		}
		return ResponseEntity.ok().build();
	}

	record ParsedMessage(String from, String profileName, String messageId, MessageContent content) {
	}

	private static final Map<String, com.omnichannel.channels.MessageKind> MEDIA_TYPES = Map.of(
			"image", com.omnichannel.channels.MessageKind.IMAGE,
			"video", com.omnichannel.channels.MessageKind.VIDEO,
			"audio", com.omnichannel.channels.MessageKind.AUDIO,
			"document", com.omnichannel.channels.MessageKind.DOCUMENT);

	/**
	 * Extrai mensagens de texto e mídia do envelope Cloud API; ignora o
	 * resto (status, stickers etc.). Falha de download de mídia propaga —
	 * o 500 faz o provedor reentregar, e o dedup por id absorve repetições.
	 */
	@SuppressWarnings("unchecked")
	private List<ParsedMessage> parse(String rawBody, ChannelDirectory.PublicChannel channel) {
		Map<String, Object> payload;
		try {
			payload = objectMapper.readValue(rawBody, new TypeReference<>() {
			});
		}
		catch (Exception e) {
			log.warn("Payload de webhook WhatsApp fora do formato esperado: {}", e.getMessage());
			return List.of();
		}
		var result = new java.util.ArrayList<ParsedMessage>();
		for (Map<String, Object> entry : (List<Map<String, Object>>) payload.getOrDefault("entry", List.of())) {
			for (Map<String, Object> change : (List<Map<String, Object>>) entry.getOrDefault("changes",
					List.of())) {
				Map<String, Object> value = (Map<String, Object>) change.getOrDefault("value", Map.of());
				String profileName = extractProfileName(value);
				for (Map<String, Object> message : (List<Map<String, Object>>) value.getOrDefault("messages",
						List.of())) {
					MessageContent content = contentOf(message, channel);
					if (content == null) {
						continue;
					}
					result.add(new ParsedMessage((String) message.get("from"), profileName,
							(String) message.get("id"), content));
				}
			}
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private MessageContent contentOf(Map<String, Object> message, ChannelDirectory.PublicChannel channel) {
		String type = (String) message.get("type");
		if ("text".equals(type)) {
			Map<String, Object> text = (Map<String, Object>) message.get("text");
			return MessageContent.text((String) text.get("body"));
		}
		var kind = MEDIA_TYPES.get(type);
		if (kind == null) {
			return null; // status de entrega, sticker, reaction etc.
		}
		Map<String, Object> media = (Map<String, Object>) message.get(type);
		String mediaId = (String) media.get("id");
		var credentials = com.omnichannel.shared.tenancy.TenantContext
			.runAsAuthProcess(() -> channelService.credentialsOf(channel.channelId()));
		var downloaded = mediaClient.download(mediaId, credentials);
		String filename = media.get("filename") instanceof String name ? name
				: mediaId + extensionOf(downloaded.mimeType());
		String storageKey = storageService.store(channel.tenantId(), filename, downloaded.mimeType(),
				downloaded.bytes());
		return MessageContent.media(kind, storageKey, downloaded.mimeType(), (String) media.get("caption"));
	}

	private String extensionOf(String mimeType) {
		int slash = mimeType.indexOf('/');
		return slash < 0 ? "" : "." + mimeType.substring(slash + 1).replaceAll("[^a-z0-9]", "");
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
