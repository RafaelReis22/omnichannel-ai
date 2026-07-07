package com.omnichannel.tenant.web;

import com.omnichannel.tenant.AuthenticationFailedException;
import com.omnichannel.tenant.EmailAlreadyRegisteredException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Erros de autenticação em formato Problem Details (RFC 9457), sem vazar
 * detalhe interno (docs/06-seguranca.md).
 */
@RestControllerAdvice
class AuthExceptionHandler {

	@ExceptionHandler(AuthenticationFailedException.class)
	ProblemDetail handleAuthenticationFailed(AuthenticationFailedException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
	}

	@ExceptionHandler(EmailAlreadyRegisteredException.class)
	ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}
}
