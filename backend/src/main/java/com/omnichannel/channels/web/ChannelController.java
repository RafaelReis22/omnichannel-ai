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
import jakarta.validation.constraints.NotEmpty;
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
			@NotEmpty Map<String, String> credentials) {
	}

	record ChannelResponse(UUID id, ChannelType type, String name, String status, Instant createdAt) {
		static ChannelResponse from(Channel channel) {
			return new ChannelResponse(channel.getId(), channel.getType(), channel.getName(),
					channel.getStatus().name(), channel.getCreatedAt());
		}
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.CREATED)
	ChannelResponse connect(@Valid @RequestBody ConnectChannelRequest request) {
		Channel channel = channelService.connect(TenantContext.getTenantId(), request.type(), request.name(),
				request.credentials());
		return ChannelResponse.from(channel);
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

	@ExceptionHandler(ChannelNotAvailableException.class)
	ProblemDetail handleChannelNotAvailable(ChannelNotAvailableException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
	}
}
