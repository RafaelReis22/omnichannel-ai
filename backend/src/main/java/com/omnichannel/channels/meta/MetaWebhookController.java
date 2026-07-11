package com.omnichannel.channels.meta;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnichannel.channels.ChannelType;
import com.omnichannel.channels.InboundMessageGateway;
import com.omnichannel.channels.InboundMessageReceived;
import com.omnichannel.channels.MessageContent;
import com.omnichannel.channels.domain.ChannelService;
import com.omnichannel.shared.tenancy.TenantContext;

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
 * Webhook único dos canais Meta (Messenger e Instagram — o campo
 * {@code object} do payload distingue). Segurança em duas camadas, ambas
 * exigidas pela Meta:
 * <ul>
 *   <li>GET de verificação: eco do {@code hub.challenge} apenas se o
 *       {@code hub.verify_token} bater com o do canal;</li>
 *   <li>POST assinado: {@code X-Hub-Signature-256} = HMAC-SHA256 do corpo
 *       BRUTO com o {@code app_secret} — por isso o body entra como String
 *       e só é parseado depois de validar.</li>
 * </ul>
 */
@RestController
class MetaWebhookController {

	private static final Logger log = LoggerFactory.getLogger(MetaWebhookController.class);
	private static final Set<ChannelType> META_TYPES = Set.of(ChannelType.MESSENGER, ChannelType.INSTAGRAM);

	private final ChannelService channelService;
	private final InboundMessageGateway gateway;
	private final ObjectMapper objectMapper;
	private final com.omnichannel.shared.storage.StorageService storageService;
	private final org.springframework.web.client.RestClient restClient = org.springframework.web.client.RestClient
		.create();

	MetaWebhookController(ChannelService channelService, InboundMessageGateway gateway, ObjectMapper objectMapper,
			com.omnichannel.shared.storage.StorageService storageService) {
		this.channelService = channelService;
		this.gateway = gateway;
		this.objectMapper = objectMapper;
		this.storageService = storageService;
	}

	private record MetaChannel(UUID channelId, UUID tenantId, ChannelType type, String appSecret,
			String verifyToken) {
	}

	@GetMapping("/api/meta/webhook/{channelId}")
	ResponseEntity<String> verify(@PathVariable UUID channelId,
			@RequestParam(name = "hub.mode", required = false) String mode,
			@RequestParam(name = "hub.verify_token", required = false) String verifyToken,
			@RequestParam(name = "hub.challenge", required = false) String challenge) {
		Optional<MetaChannel> channel = resolve(channelId);
		if (channel.isEmpty() || !"subscribe".equals(mode) || verifyToken == null
				|| !constantTimeEquals(channel.get().verifyToken(), verifyToken)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		return ResponseEntity.ok(challenge);
	}

	@PostMapping("/api/meta/webhook/{channelId}")
	ResponseEntity<Void> receive(@PathVariable UUID channelId,
			@RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
			@RequestBody String rawBody) {
		Optional<MetaChannel> resolved = resolve(channelId);
		if (resolved.isEmpty() || signature == null
				|| !constantTimeEquals("sha256=" + MetaSignatures.hmacSha256(resolved.get().appSecret(), rawBody), signature)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		MetaChannel channel = resolved.get();
		for (ParsedMessage parsed : parse(rawBody, channel)) {
			gateway.receive(new InboundMessageReceived(channel.tenantId(), channel.channelId(), channel.type(),
					parsed.senderId(), null, parsed.messageId(), parsed.content(), Instant.now()));
		}
		return ResponseEntity.ok().build();
	}

	private Optional<MetaChannel> resolve(UUID channelId) {
		return TenantContext.runAsAuthProcess(() -> channelService.findActiveOfAnyType(channelId, META_TYPES)
			.map(channel -> {
				var credentials = channelService.credentialsOf(channel.getId());
				return new MetaChannel(channel.getId(), channel.getTenantId(), channel.getType(),
						credentials.values().get("app_secret"), credentials.values().get("verify_token"));
			})
			.filter(channel -> channel.appSecret() != null && channel.verifyToken() != null));
	}

	private record ParsedMessage(String senderId, String messageId, MessageContent content) {
	}

	/**
	 * Extrai texto e anexos do envelope Messenger Platform; ignora ecos e o
	 * resto. Anexo vem com URL de CDN efêmera — os bytes são baixados na
	 * hora e guardados no object storage (falha propaga: o 500 faz a Meta
	 * reentregar e o dedup por mid absorve repetições). Texto que acompanha
	 * anexo vira legenda do primeiro; anexos extras viram mensagens próprias
	 * com sufixo no mid para o dedup.
	 */
	private List<ParsedMessage> parse(String rawBody, MetaChannel channel) {
		Map<String, Object> payload;
		try {
			payload = objectMapper.readValue(rawBody, new TypeReference<>() {
			});
		}
		catch (Exception e) {
			log.warn("Payload de webhook Meta fora do formato esperado: {}", e.getMessage());
			return List.of();
		}
		String object = (String) payload.get("object");
		boolean matches = (channel.type() == ChannelType.MESSENGER && "page".equals(object))
				|| (channel.type() == ChannelType.INSTAGRAM && "instagram".equals(object));
		if (!matches) {
			return List.of();
		}
		var result = new java.util.ArrayList<ParsedMessage>();
		for (Map<String, Object> entry : castList(payload.get("entry"))) {
			for (Map<String, Object> messaging : castList(entry.get("messaging"))) {
				Map<String, Object> message = castMap(messaging.get("message"));
				if (message == null || Boolean.TRUE.equals(message.get("is_echo"))) {
					continue;
				}
				Map<String, Object> sender = castMap(messaging.get("sender"));
				String senderId = (String) sender.get("id");
				String mid = (String) message.get("mid");
				String text = message.get("text") instanceof String value ? value : null;

				List<Map<String, Object>> attachments = castList(message.get("attachments"));
				if (attachments.isEmpty()) {
					if (text != null) {
						result.add(new ParsedMessage(senderId, mid, MessageContent.text(text)));
					}
					continue;
				}
				for (int i = 0; i < attachments.size(); i++) {
					MessageContent content = attachmentContent(attachments.get(i), i == 0 ? text : null, channel);
					if (content != null) {
						result.add(new ParsedMessage(senderId, i == 0 ? mid : mid + "#" + i, content));
					}
				}
			}
		}
		return result;
	}

	private static final Map<String, com.omnichannel.channels.MessageKind> ATTACHMENT_KINDS = Map.of(
			"image", com.omnichannel.channels.MessageKind.IMAGE,
			"video", com.omnichannel.channels.MessageKind.VIDEO,
			"audio", com.omnichannel.channels.MessageKind.AUDIO,
			"file", com.omnichannel.channels.MessageKind.DOCUMENT);

	private MessageContent attachmentContent(Map<String, Object> attachment, String caption, MetaChannel channel) {
		var kind = ATTACHMENT_KINDS.get((String) attachment.get("type"));
		Map<String, Object> attachmentPayload = castMap(attachment.get("payload"));
		if (kind == null || attachmentPayload == null
				|| !(attachmentPayload.get("url") instanceof String url)) {
			return null; // fallback/template/localização etc.
		}
		// URI pronto, não String: URL de CDN traz query assinada com % que o
		// modo template do RestClient re-encodaria
		var response = restClient.get().uri(java.net.URI.create(url)).retrieve().toEntity(byte[].class);
		String mimeType = response.getHeaders().getContentType() == null ? "application/octet-stream"
				: response.getHeaders().getContentType().toString();
		String filename = lastPathSegment(url);
		String storageKey = storageService.store(channel.tenantId(), filename, mimeType, response.getBody());
		return MessageContent.media(kind, storageKey, mimeType, caption);
	}

	private String lastPathSegment(String url) {
		try {
			String path = java.net.URI.create(url).getPath();
			String last = path.substring(path.lastIndexOf('/') + 1);
			return last.isBlank() ? "anexo" : last;
		}
		catch (Exception e) {
			return "anexo";
		}
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> castList(Object value) {
		return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> castMap(Object value) {
		return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
	}

	private static boolean constantTimeEquals(String expected, String provided) {
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
				provided.getBytes(StandardCharsets.UTF_8));
	}
}
