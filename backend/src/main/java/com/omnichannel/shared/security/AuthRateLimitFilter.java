package com.omnichannel.shared.security;

import java.io.IOException;

import com.omnichannel.shared.ratelimit.RateLimiter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limite por IP em todas as rotas de autenticação — a primeira barreira
 * contra força bruta e enumeração (docs/06-seguranca.md). O limite por
 * conta-alvo fica no AuthService, que conhece o e-mail.
 *
 * <p>O IP vem de {@code getRemoteAddr()} com
 * {@code server.forward-headers-strategy: framework}: atrás do load
 * balancer da plataforma, o X-Forwarded-For é honrado; exposição direta à
 * internet sem proxy tornaria o cabeçalho forjável — cenário que o deploy
 * (docs/07-deploy.md) não prevê.
 */
@Component
class AuthRateLimitFilter extends OncePerRequestFilter {

	private final RateLimiter rateLimiter;
	private final RateLimitProperties properties;

	AuthRateLimitFilter(RateLimiter rateLimiter, RateLimitProperties properties) {
		this.rateLimiter = rateLimiter;
		this.properties = properties;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/auth/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String key = "rl:auth:ip:" + request.getRemoteAddr();
		if (rateLimiter.tryAcquire(key, properties.ipLimit(), properties.ipWindow())) {
			chain.doFilter(request, response);
			return;
		}
		long retryAfter = rateLimiter.secondsUntilReset(key, properties.ipWindow());
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		response.setHeader("Retry-After", String.valueOf(retryAfter));
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.getWriter()
			.write("""
					{"type":"about:blank","title":"Too Many Requests","status":429,\
					"detail":"Muitas tentativas deste endereço. Tente novamente em %d segundos."}"""
				.formatted(retryAfter));
	}
}
