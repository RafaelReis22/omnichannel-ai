package com.omnichannel.tenant;

/** Credenciais ou refresh token inválidos — sempre mapeada para 401. */
public class AuthenticationFailedException extends RuntimeException {

	public AuthenticationFailedException(String message) {
		super(message);
	}
}
