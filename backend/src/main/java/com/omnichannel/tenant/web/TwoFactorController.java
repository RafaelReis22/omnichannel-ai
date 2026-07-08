package com.omnichannel.tenant.web;

import java.util.List;
import java.util.UUID;

import com.omnichannel.tenant.AuthService;
import com.omnichannel.tenant.TwoFactorService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Enrolamento de 2FA do próprio usuário autenticado. Rotas fora de
 * /api/auth/** de propósito: exigem access token válido.
 */
@RestController
@RequestMapping("/api/2fa")
class TwoFactorController {

	private final AuthService authService;

	TwoFactorController(AuthService authService) {
		this.authService = authService;
	}

	record ConfirmRequest(@NotBlank String code) {
	}

	record EnrollmentResponse(String secret, String otpauthUri) {
	}

	record RecoveryCodesResponse(List<String> recoveryCodes) {
	}

	@PostMapping("/setup")
	EnrollmentResponse setup(JwtAuthenticationToken authentication) {
		TwoFactorService.TotpEnrollment enrollment = authService
			.startTotpEnrollment(UUID.fromString(authentication.getToken().getSubject()));
		return new EnrollmentResponse(enrollment.secret(), enrollment.otpauthUri());
	}

	@PostMapping("/confirm")
	RecoveryCodesResponse confirm(JwtAuthenticationToken authentication, @Valid @RequestBody ConfirmRequest request) {
		List<String> recoveryCodes = authService
			.confirmTotpEnrollment(UUID.fromString(authentication.getToken().getSubject()), request.code());
		return new RecoveryCodesResponse(recoveryCodes);
	}
}
