package com.omnichannel.tenant.web;

import com.omnichannel.shared.ratelimit.RateLimitExceededException;
import com.omnichannel.tenant.AuthenticationFailedException;
import com.omnichannel.tenant.EmailAlreadyRegisteredException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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

	@ExceptionHandler(RateLimitExceededException.class)
	ResponseEntity<ProblemDetail> handleRateLimitExceeded(RateLimitExceededException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
				exception.getMessage());
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()));
		return new ResponseEntity<>(problem, headers, HttpStatus.TOO_MANY_REQUESTS);
	}
}
