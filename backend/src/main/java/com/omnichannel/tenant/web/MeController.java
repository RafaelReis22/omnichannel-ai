package com.omnichannel.tenant.web;

import java.util.UUID;

import com.omnichannel.tenant.domain.User;
import com.omnichannel.tenant.domain.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Dados do usuário autenticado. A busca por id passa pelo RLS com o tenant
 * do JWT — se o token apontar para um usuário de outro tenant, o banco
 * simplesmente não retorna a linha.
 */
@RestController
class MeController {

	private final UserRepository userRepository;

	MeController(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	record MeResponse(UUID id, UUID tenantId, String name, String email, String role) {
	}

	@GetMapping("/api/me")
	MeResponse me(JwtAuthenticationToken authentication) {
		UUID userId = UUID.fromString(authentication.getToken().getSubject());
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não encontrado"));
		return new MeResponse(user.getId(), user.getTenantId(), user.getName(), user.getEmail(),
				user.getRole().name());
	}
}
