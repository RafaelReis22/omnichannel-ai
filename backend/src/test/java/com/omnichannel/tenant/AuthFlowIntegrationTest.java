package com.omnichannel.tenant;

import java.util.Map;
import java.util.UUID;

import com.omnichannel.TestcontainersConfiguration;

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

/** Fluxo de autenticação de ponta a ponta contra a API real. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AuthFlowIntegrationTest {

	@Autowired
	TestRestTemplate rest;

	String email;

	@BeforeEach
	void geraEmailUnico() {
		email = "dono-" + UUID.randomUUID() + "@empresa.com";
	}

	@Test
	void signupLoginMeERefreshFuncionamEmSequencia() {
		// signup cria tenant + admin e já autentica
		ResponseEntity<Map> signup = rest.postForEntity("/api/auth/signup",
				Map.of("tenantName", "Empresa Teste", "userName", "Dona", "email", email, "password", "senha-forte-1"),
				Map.class);
		assertThat(signup.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(signup.getBody()).containsKeys("accessToken", "refreshToken");

		// login com as mesmas credenciais
		ResponseEntity<Map> login = rest.postForEntity("/api/auth/login",
				Map.of("email", email, "password", "senha-forte-1"), Map.class);
		assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
		String accessToken = (String) login.getBody().get("accessToken");
		String refreshToken = (String) login.getBody().get("refreshToken");

		// /api/me com o access token — atravessa JWT + interceptor + RLS
		ResponseEntity<Map> me = withBearer(accessToken, "/api/me");
		assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(me.getBody()).containsEntry("email", email).containsKey("tenantId");

		// refresh rotaciona: novo par válido, token antigo morre
		ResponseEntity<Map> refreshed = rest.postForEntity("/api/auth/refresh",
				Map.of("refreshToken", refreshToken), Map.class);
		assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(refreshed.getBody()).containsKeys("accessToken", "refreshToken");

		ResponseEntity<Map> reuseRevoked = rest.postForEntity("/api/auth/refresh",
				Map.of("refreshToken", refreshToken), Map.class);
		assertThat(reuseRevoked.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void senhaErradaEEmailInexistenteRetornam401SemDistincao() {
		rest.postForEntity("/api/auth/signup", Map.of("tenantName", "Empresa", "userName", "Dona", "email", email,
				"password", "senha-forte-1"), Map.class);

		ResponseEntity<Map> senhaErrada = rest.postForEntity("/api/auth/login",
				Map.of("email", email, "password", "senha-errada"), Map.class);
		ResponseEntity<Map> emailInexistente = rest.postForEntity("/api/auth/login",
				Map.of("email", "ninguem-" + UUID.randomUUID() + "@x.com", "password", "tanto-faz"), Map.class);

		assertThat(senhaErrada.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(emailInexistente.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(senhaErrada.getBody().get("detail")).isEqualTo(emailInexistente.getBody().get("detail"));
	}

	@Test
	void emailDuplicadoRetorna409() {
		Map<String, String> body = Map.of("tenantName", "Empresa", "userName", "Dona", "email", email, "password",
				"senha-forte-1");
		rest.postForEntity("/api/auth/signup", body, Map.class);

		ResponseEntity<Map> duplicado = rest.postForEntity("/api/auth/signup", body, Map.class);
		assertThat(duplicado.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void rotaProtegidaSemTokenRetorna401() {
		ResponseEntity<Map> semToken = rest.getForEntity("/api/me", Map.class);
		assertThat(semToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void signupComPayloadInvalidoRetorna400() {
		ResponseEntity<Map> invalido = rest.postForEntity("/api/auth/signup",
				Map.of("tenantName", "", "userName", "X", "email", "nao-e-email", "password", "curta"), Map.class);
		assertThat(invalido.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	private ResponseEntity<Map> withBearer(String token, String path) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
	}
}
