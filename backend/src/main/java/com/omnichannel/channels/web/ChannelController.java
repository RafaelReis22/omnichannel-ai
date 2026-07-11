package com.omnichannel.channels.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.omnichannel.channels.ChannelNotAvailableException;
import com.omnichannel.channels.ChannelType;
import com.omnichannel.channels.domain.Channel;
import com.omnichannel.channels.domain.ChannelService;
import com.omnichannel.shared.tenancy.TenantContext;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuração de canais do tenant. Conectar/desabilitar é ação sensível —
 * Admin apenas (docs/06-seguranca.md); listar é aberto a qualquer papel.
 * Credenciais NUNCA aparecem em resposta.
 */
@RestController
@RequestMapping("/api/channels")
class ChannelController {

	private final ChannelService channelService;

	ChannelController(ChannelService channelService) {
		this.channelService = channelService;
	}

	record ConnectChannelRequest(
			@NotNull ChannelType type,
			@NotBlank @Size(max = 120) String name,
			// webchat não usa credenciais; canais que usam (e-mail, WhatsApp)
			// validam as chaves obrigatórias no momento do uso
			Map<String, String> credentials) {

		Map<String, String> credentialsOrEmpty() {
			return credentials == null ? Map.of() : credentials;
		}
	}

	record ChannelResponse(UUID id, ChannelType type, String name, String status, Instant createdAt,
			String webhookPath, String verifyToken) {
		static ChannelResponse from(Channel channel) {
			return new ChannelResponse(channel.getId(), channel.getType(), channel.getName(),
					channel.getStatus().name(), channel.getCreatedAt(), null, null);
		}
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.CREATED)
	ChannelResponse connect(@Valid @RequestBody ConnectChannelRequest request) {
		Map<String, String> credentials = new java.util.HashMap<>(request.credentialsOrEmpty());
		String webhookToken = null;
		String verifyToken = null;
		if (request.type() == ChannelType.WHATSAPP && !credentials.containsKey("webhook_token")) {
			// token secreto na URL: autenticação p/ 360dialog e, na Cloud API,
			// também o verify token digitado no painel Meta for Developers.
			// Exibido UMA única vez, aqui — depois vive só criptografado.
			webhookToken = randomToken();
			credentials.put("webhook_token", webhookToken);
			verifyToken = webhookToken;
		}
		if ((request.type() == ChannelType.MESSENGER || request.type() == ChannelType.INSTAGRAM)
				&& !credentials.containsKey("verify_token")) {
			// verificação de endpoint da Meta (hub.challenge) — idem: uma exibição
			verifyToken = randomToken();
			credentials.put("verify_token", verifyToken);
		}
		Channel channel = channelService.connect(TenantContext.getTenantId(), request.type(), request.name(),
				credentials);
		String webhookPath = switch (request.type()) {
			case WHATSAPP -> "/api/whatsapp/webhook/" + channel.getId() + "/" + webhookToken;
			case MESSENGER, INSTAGRAM -> "/api/meta/webhook/" + channel.getId();
			default -> null;
		};
		return new ChannelResponse(channel.getId(), channel.getType(), channel.getName(),
				channel.getStatus().name(), channel.getCreatedAt(), webhookPath, verifyToken);
	}

	private static String randomToken() {
		byte[] random = new byte[32];
		new java.security.SecureRandom().nextBytes(random);
		return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(random);
	}

	@GetMapping
	List<ChannelResponse> list() {
		return channelService.list().stream().map(ChannelResponse::from).toList();
	}

	@PostMapping("/{id}/disable")
	@PreAuthorize("hasRole('ADMIN')")
	ChannelResponse disable(@PathVariable UUID id) {
		return ChannelResponse.from(channelService.disable(id));
	}

	@PostMapping("/{id}/activate")
	@PreAuthorize("hasRole('ADMIN')")
	ChannelResponse activate(@PathVariable UUID id) {
		return ChannelResponse.from(channelService.activate(id));
	}

	/** Rotação de token/segredo sem reconectar: mescla com as credenciais atuais. */
	@org.springframework.web.bind.annotation.PutMapping("/{id}/credentials")
	@PreAuthorize("hasRole('ADMIN')")
	ChannelResponse updateCredentials(@PathVariable UUID id,
			@RequestBody Map<String, String> credentials) {
		return ChannelResponse.from(channelService.updateCredentials(id, credentials));
	}

	@org.springframework.web.bind.annotation.DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable UUID id) {
		channelService.delete(id);
	}

	@ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
	ProblemDetail handleChannelInUse(org.springframework.dao.DataIntegrityViolationException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"Canal tem conversas no histórico — desative em vez de excluir.");
	}

	@ExceptionHandler(ChannelNotAvailableException.class)
	ProblemDetail handleChannelNotAvailable(ChannelNotAvailableException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
	}
}
