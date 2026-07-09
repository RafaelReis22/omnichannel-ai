package com.omnichannel.inbox.realtime;

import java.util.Map;
import java.util.UUID;

import com.omnichannel.shared.security.JwtService;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Endpoint {@code /ws?token=<access token>}: o browser não envia header
 * Authorization em WebSocket, então o JWT vem na query e é validado no
 * handshake pelo MESMO decoder do resource server — inclusive a rejeição
 * de tokens de desafio MFA. Sem token válido, o handshake é recusado.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
class RealtimeWebSocketConfig implements WebSocketConfigurer {

	private final InboxRealtimeHandler handler;
	private final JwtDecoder jwtDecoder;

	RealtimeWebSocketConfig(InboxRealtimeHandler handler, JwtDecoder jwtDecoder) {
		this.handler = handler;
		this.jwtDecoder = jwtDecoder;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(handler, "/ws")
			.addInterceptors(jwtHandshakeInterceptor())
			.setAllowedOriginPatterns("*"); // autenticação é pelo token, não pela origem
	}

	private HandshakeInterceptor jwtHandshakeInterceptor() {
		return new HandshakeInterceptor() {
			@Override
			public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
					WebSocketHandler wsHandler, Map<String, Object> attributes) {
				String token = UriComponentsBuilder.fromUri(request.getURI())
					.build()
					.getQueryParams()
					.getFirst("token");
				if (token == null || token.isBlank()) {
					return false;
				}
				try {
					Jwt jwt = jwtDecoder.decode(token);
					attributes.put(InboxRealtimeHandler.TENANT_ATTRIBUTE,
							UUID.fromString(jwt.getClaimAsString(JwtService.CLAIM_TENANT_ID)));
					return true;
				}
				catch (JwtException | IllegalArgumentException | NullPointerException e) {
					return false;
				}
			}

			@Override
			public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
					WebSocketHandler wsHandler, Exception exception) {
			}
		};
	}
}
