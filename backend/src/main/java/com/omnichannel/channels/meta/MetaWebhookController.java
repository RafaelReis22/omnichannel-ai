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

	MetaWebhookController(ChannelService channelService, InboundMessageGateway gateway, ObjectMapper objectMapper) {
		this.channelService = channelService;
		this.gateway = gateway;
		this.objectMapper = objectMapper;
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
		for (ParsedMessage parsed : parse(rawBody, channel.type())) {
			gateway.receive(new InboundMessageReceived(channel.tenantId(), channel.channelId(), channel.type(),
					parsed.senderId(), null, parsed.messageId(), MessageContent.text(parsed.text()),
					Instant.now()));
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

	private record ParsedMessage(String senderId, String messageId, String text) {
	}

	/** Extrai mensagens de texto do envelope Messenger Platform; ignora ecos e o resto. */
	private List<ParsedMessage> parse(String rawBody, ChannelType channelType) {
		try {
			Map<String, Object> payload = objectMapper.readValue(rawBody, new TypeReference<>() {
			});
			String object = (String) payload.get("object");
			boolean matches = (channelType == ChannelType.MESSENGER && "page".equals(object))
					|| (channelType == ChannelType.INSTAGRAM && "instagram".equals(object));
			if (!matches) {
				return List.of();
			}
			var result = new java.util.ArrayList<ParsedMessage>();
			for (Map<String, Object> entry : castList(payload.get("entry"))) {
				for (Map<String, Object> messaging : castList(entry.get("messaging"))) {
					Map<String, Object> message = castMap(messaging.get("message"));
					if (message == null || Boolean.TRUE.equals(message.get("is_echo"))
							|| !(message.get("text") instanceof String text)) {
						continue;
					}
					Map<String, Object> sender = castMap(messaging.get("sender"));
					result.add(new ParsedMessage((String) sender.get("id"), (String) message.get("mid"), text));
				}
			}
			return result;
		}
		catch (Exception e) {
			log.warn("Payload de webhook Meta fora do formato esperado: {}", e.getMessage());
			return List.of();
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
