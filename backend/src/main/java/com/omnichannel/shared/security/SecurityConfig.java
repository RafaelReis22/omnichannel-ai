package com.omnichannel.shared.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import jakarta.servlet.DispatcherType;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * API stateless: JWT em toda rota exceto autenticação e health check.
 * CSRF desabilitado por não haver sessão de navegador (docs/06-seguranca.md
 * — o dashboard consome a API com bearer token, não com cookie).
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({ SecurityProperties.class, RateLimitProperties.class })
class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
			.cors(cors -> {
			})
			.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(authorize -> authorize
				// dispatch de erro interno (ex.: 400 de validação) não é uma
				// requisição do cliente — sem isso o Security devolveria 401
				.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
				.requestMatchers("/api/auth/**", "/actuator/health/**", "/actuator/health").permitAll()
				// widget público de webchat: visitante anônimo, channelId como site key
				.requestMatchers("/api/webchat/**").permitAll()
				// contrato da API e UI de exploração; reavaliar exposição pública na Fase 4
				.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
				.anyRequest().authenticated())
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
			.exceptionHandling(handling -> handling.defaultAuthenticationEntryPointFor(
					(request, response, exception) -> response.setStatus(HttpStatus.UNAUTHORIZED.value()),
					AntPathRequestMatcher.antMatcher("/api/**")))
			.build();
	}

	/** Converte a claim {@code role} do nosso JWT em authority ROLE_* — sem isso, hasRole nunca casa. */
	private static JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(jwt -> {
			String role = jwt.getClaimAsString(JwtService.CLAIM_ROLE);
			return role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));
		});
		return converter;
	}

	/**
	 * CORS para o dashboard (origens via CORS_ALLOWED_ORIGINS). O webchat
	 * público também depende disto: o widget roda no site do cliente.
	 */
	@Bean
	org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource(
			@org.springframework.beans.factory.annotation.Value("${app.security.cors-allowed-origins:http://localhost:3000}") List<String> allowedOrigins) {
		var dashboard = new org.springframework.web.cors.CorsConfiguration();
		dashboard.setAllowedOrigins(allowedOrigins);
		dashboard.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		dashboard.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		dashboard.setMaxAge(3600L);

		// widget: qualquer site pode falar com o webchat (canal público por design)
		var webchat = new org.springframework.web.cors.CorsConfiguration();
		webchat.setAllowedOriginPatterns(List.of("*"));
		webchat.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
		webchat.setAllowedHeaders(List.of("Content-Type"));
		webchat.setMaxAge(3600L);

		var source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/webchat/**", webchat);
		source.registerCorsConfiguration("/**", dashboard);
		return source;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		// Argon2id com os parâmetros recomendados pelo Spring Security
		return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
	}

	@Bean
	JwtEncoder jwtEncoder(SecurityProperties properties) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
	}

	@Bean
	JwtDecoder jwtDecoder(SecurityProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		// O token intermediário do login com 2FA (claim mfa=pending) é assinado
		// com a mesma chave — sem esta validação ele autenticaria qualquer rota.
		OAuth2TokenValidator<Jwt> rejectMfaPending = jwt -> jwt.hasClaim(JwtService.CLAIM_MFA)
				? OAuth2TokenValidatorResult
					.failure(new OAuth2Error("invalid_token", "Token de desafio MFA não autentica rotas", null))
				: OAuth2TokenValidatorResult.success();
		decoder.setJwtValidator(
				new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), rejectMfaPending));
		return decoder;
	}

	private static SecretKey secretKey(SecurityProperties properties) {
		return new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}
}
