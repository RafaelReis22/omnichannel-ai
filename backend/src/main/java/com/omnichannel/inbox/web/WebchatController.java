package com.omnichannel.inbox.web;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.omnichannel.channels.ChannelDirectory;
import com.omnichannel.channels.ChannelType;
import com.omnichannel.channels.InboundMessageGateway;
import com.omnichannel.channels.InboundMessageReceived;
import com.omnichannel.channels.MessageContent;
import com.omnichannel.inbox.InboxService;
import com.omnichannel.inbox.domain.Message;
import com.omnichannel.shared.ratelimit.RateLimitExceededException;
import com.omnichannel.shared.ratelimit.RateLimiter;
import com.omnichannel.shared.tenancy.TenantContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API pública do widget de webchat — sem JWT: o visitante é anônimo e o
 * channelId funciona como site key (UUID não enumerável). Proteções:
 * canal precisa existir, estar ativo e ser WEBCHAT; rate limit por IP e
 * por visitante; o tenant nunca vem do cliente — sempre do canal.
 */
@RestController
@RequestMapping("/api/webchat/{channelId}")
class WebchatController {

	// dimensionados para widget com polling (~1 req/3s por visitante,
	// vários visitantes atrás do mesmo IP corporativo)
	private static final int IP_LIMIT_PER_MINUTE = 300;
	private static final int VISITOR_LIMIT_PER_MINUTE = 60;
	private static final Duration WINDOW = Duration.ofMinutes(1);
	private static final Instant EPOCH = Instant.EPOCH;

	private final ChannelDirectory channelDirectory;
	private final InboundMessageGateway gateway;
	private final InboxService inboxService;
	private final RateLimiter rateLimiter;
	private final com.omnichannel.shared.storage.StorageService storageService;

	WebchatController(ChannelDirectory channelDirectory, InboundMessageGateway gateway, InboxService inboxService,
			RateLimiter rateLimiter, com.omnichannel.shared.storage.StorageService storageService) {
		this.channelDirectory = channelDirectory;
		this.gateway = gateway;
		this.inboxService = inboxService;
		this.rateLimiter = rateLimiter;
		this.storageService = storageService;
	}

	record VisitorMessageRequest(
			@NotBlank @Size(max = 80) String visitorId,
			@Size(max = 120) String visitorName,
			@NotBlank @Size(max = 4000) String text,
			@Size(max = 80) String clientMessageId) {
	}

	record VisitorMessageResponse(UUID id, String sender, String kind, String text, String mediaUrl,
			Instant createdAt) {
	}

	private VisitorMessageResponse toVisitorResponse(Message message) {
		return new VisitorMessageResponse(message.getId(), message.getSender().name(), message.getKind().name(),
				message.getText(), storageService.resolveUrl(message.getMediaUrl()), message.getCreatedAt());
	}

	@PostMapping("/messages")
	@ResponseStatus(HttpStatus.ACCEPTED)
	void send(@PathVariable UUID channelId, @Valid @RequestBody VisitorMessageRequest request,
			HttpServletRequest httpRequest) {
		enforceRateLimits(httpRequest, request.visitorId());
		ChannelDirectory.PublicChannel channel = requireWebchat(channelId);
		// dedup por reenvio do widget: escopado ao visitante para um cliente
		// não conseguir "roubar" o id de mensagem de outro
		String externalMessageId = request.clientMessageId() == null ? null
				: "webchat:" + request.visitorId() + ":" + request.clientMessageId();
		gateway.receive(new InboundMessageReceived(channel.tenantId(), channel.channelId(), ChannelType.WEBCHAT,
				request.visitorId(), request.visitorName(), externalMessageId,
				MessageContent.text(request.text()), Instant.now()));
	}

	@GetMapping("/messages")
	List<VisitorMessageResponse> poll(@PathVariable UUID channelId,
			@RequestParam @NotBlank @Size(max = 80) String visitorId,
			@RequestParam(required = false) Instant after,
			HttpServletRequest httpRequest) {
		enforceRateLimits(httpRequest, visitorId);
		ChannelDirectory.PublicChannel channel = requireWebchat(channelId);
		TenantContext.setTenantId(channel.tenantId());
		try {
			return inboxService.webchatThread(channelId, visitorId, after == null ? EPOCH : after)
				.stream()
				.map(this::toVisitorResponse)
				.toList();
		}
		finally {
			TenantContext.clear();
		}
	}

	private ChannelDirectory.PublicChannel requireWebchat(UUID channelId) {
		return channelDirectory.findActiveWebchat(channelId)
			.orElseThrow(() -> new WebchatChannelNotFoundException());
	}

	private void enforceRateLimits(HttpServletRequest request, String visitorId) {
		String ipKey = "rl:webchat:ip:" + request.getRemoteAddr();
		String visitorKey = "rl:webchat:visitor:" + visitorId;
		if (!rateLimiter.tryAcquire(ipKey, IP_LIMIT_PER_MINUTE, WINDOW)
				|| !rateLimiter.tryAcquire(visitorKey, VISITOR_LIMIT_PER_MINUTE, WINDOW)) {
			throw new RateLimitExceededException("Muitas requisições. Tente novamente em instantes.",
					WINDOW.toSeconds());
		}
	}

	static class WebchatChannelNotFoundException extends RuntimeException {
	}

	@ExceptionHandler(WebchatChannelNotFoundException.class)
	ProblemDetail handleChannelNotFound(WebchatChannelNotFoundException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Canal de webchat inexistente ou inativo");
	}

	@ExceptionHandler(RateLimitExceededException.class)
	ResponseEntity<ProblemDetail> handleRateLimit(RateLimitExceededException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
				exception.getMessage());
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()));
		return new ResponseEntity<>(problem, headers, HttpStatus.TOO_MANY_REQUESTS);
	}
}
