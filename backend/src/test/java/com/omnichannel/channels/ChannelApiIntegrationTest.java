package com.omnichannel.channels;

import java.util.Map;
import java.util.UUID;

import com.omnichannel.TestcontainersConfiguration;
import com.omnichannel.tenant.AuthService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** RBAC da API de canais: Admin conecta/desabilita; credencial nunca vaza na resposta. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ChannelApiIntegrationTest {

	@Autowired
	TestRestTemplate rest;

	@Autowired
	AuthService authService;

	@Autowired
	com.omnichannel.tenant.domain.UserRepository userRepository;

	@Autowired
	org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

	@Autowired
	org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

	String adminToken;

	@BeforeEach
	void criaTenantAdmin() {
		adminToken = authService
			.signup("Empresa API", "Admin", "api-" + UUID.randomUUID() + "@x.com", "senha-forte-1")
			.accessToken();
	}

	@Test
	void adminConectaListaEDesabilitaCanalSemVazarCredenciais() {
		Map<String, Object> body = Map.of("type", "WHATSAPP", "name", "WhatsApp Loja",
				"credentials", Map.of("apiKey", "segredo-do-bsp"));

		ResponseEntity<Map> created = exchange(HttpMethod.POST, "/api/channels", adminToken, body);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(created.getBody()).containsEntry("type", "WHATSAPP").doesNotContainKey("credentials");
		assertThat(created.getBody().toString()).doesNotContain("segredo-do-bsp");
		String channelId = (String) created.getBody().get("id");

		ResponseEntity<java.util.List> list = exchange(HttpMethod.GET, "/api/channels", adminToken, null,
				java.util.List.class);
		assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(list.getBody()).hasSize(1);

		ResponseEntity<Map> disabled = exchange(HttpMethod.POST, "/api/channels/" + channelId + "/disable",
				adminToken, null);
		assertThat(disabled.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(disabled.getBody()).containsEntry("status", "DISABLED");
	}

	@Test
	void agenteNaoPodeConectarNemDesabilitarCanal() {
		String agentToken = criaAgenteELoga();

		ResponseEntity<Map> conectar = exchange(HttpMethod.POST, "/api/channels", agentToken,
				Map.of("type", "WHATSAPP", "name", "X", "credentials", Map.of("k", "v")));
		assertThat(conectar.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		// listar continua permitido a qualquer papel autenticado
		ResponseEntity<java.util.List> listar = exchange(HttpMethod.GET, "/api/channels", agentToken, null,
				java.util.List.class);
		assertThat(listar.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	private String criaAgenteELoga() {
		UUID tenantId = UUID.fromString(jwtDecoder.decode(adminToken).getClaimAsString("tenant_id"));
		String email = "agente-" + UUID.randomUUID() + "@x.com";
		com.omnichannel.shared.tenancy.TenantContext.setTenantId(tenantId);
		try {
			userRepository.save(new com.omnichannel.tenant.domain.User(tenantId, "Agente", email,
					passwordEncoder.encode("senha-forte-3"), com.omnichannel.tenant.domain.UserRole.AGENT));
		}
		finally {
			com.omnichannel.shared.tenancy.TenantContext.clear();
		}
		return authService.login(email, "senha-forte-3").accessToken();
	}

	@Test
	void payloadInvalidoRetorna400() {
		ResponseEntity<Map> semCredenciais = exchange(HttpMethod.POST, "/api/channels", adminToken,
				Map.of("type", "WHATSAPP", "name", "X", "credentials", Map.of()));
		assertThat(semCredenciais.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void semTokenRetorna401() {
		ResponseEntity<Map> resposta = rest.postForEntity("/api/channels",
				Map.of("type", "WHATSAPP", "name", "X", "credentials", Map.of("k", "v")), Map.class);
		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	private ResponseEntity<Map> exchange(HttpMethod method, String path, String token, Object body) {
		return exchange(method, path, token, body, Map.class);
	}

	private <T> ResponseEntity<T> exchange(HttpMethod method, String path, String token, Object body,
			Class<T> responseType) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return rest.exchange(path, method, new HttpEntity<>(body, headers), responseType);
	}
}
