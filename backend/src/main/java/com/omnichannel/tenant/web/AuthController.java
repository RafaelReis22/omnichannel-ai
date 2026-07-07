package com.omnichannel.tenant.web;

import com.omnichannel.tenant.AuthService;
import com.omnichannel.tenant.AuthTokens;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

	private final AuthService authService;

	AuthController(AuthService authService) {
		this.authService = authService;
	}

	record SignupRequest(
			@NotBlank @Size(max = 120) String tenantName,
			@NotBlank @Size(max = 120) String userName,
			@NotBlank @Email @Size(max = 254) String email,
			@NotBlank @Size(min = 8, max = 72) String password) {
	}

	record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
	}

	record RefreshRequest(@NotBlank String refreshToken) {
	}

	record TokenResponse(String accessToken, String refreshToken, long expiresIn, String tokenType) {
		static TokenResponse from(AuthTokens tokens) {
			return new TokenResponse(tokens.accessToken(), tokens.refreshToken(),
					tokens.accessTokenExpiresInSeconds(), "Bearer");
		}
	}

	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	TokenResponse signup(@Valid @RequestBody SignupRequest request) {
		return TokenResponse
			.from(authService.signup(request.tenantName(), request.userName(), request.email(), request.password()));
	}

	@PostMapping("/login")
	TokenResponse login(@Valid @RequestBody LoginRequest request) {
		return TokenResponse.from(authService.login(request.email(), request.password()));
	}

	@PostMapping("/refresh")
	TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
		return TokenResponse.from(authService.refresh(request.refreshToken()));
	}
}
