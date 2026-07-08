package com.omnichannel.tenant;

import java.util.Map;
import java.util.UUID;

import com.omnichannel.TestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Força bruta em autenticação (docs/06-seguranca.md). Limites baixos via
 * properties — contexto Spring próprio, Redis próprio, sem interferir na
 * suíte.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"app.security.rate-limit.ip-limit=8",
		"app.security.rate-limit.ip-window=1m",
		"app.security.rate-limit.email-failure-limit=3",
		"app.security.rate-limit.email-failure-window=15m" })
@Import(TestcontainersConfiguration.class)
class RateLimitIntegrationTest {

	@Autowired
	TestRestTemplate rest;

	@Autowired
	AuthService authService;

	@Autowired
	org.springframework.data.redis.core.StringRedisTemplate redis;

	@org.junit.jupiter.api.AfterEach
	void limpaContadores() {
		// mesmo IP de loopback em todos os testes — sem limpar, um teste
		// contaminaria o limite do próximo
		redis.getConnectionFactory().getConnection().serverCommands().flushDb();
	}

	@Test
	void contaEBloqueadaAposFalhasSeguidasEDesbloqueioNaoVemDeSenhaCerta() {
		String email = "vitima-" + UUID.randomUUID() + "@x.com";
		authService.signup("Empresa", "Vitima", email, "senha-correta-1");

		// 3 falhas esgotam o limite da conta
		for (int i = 0; i < 3; i++) {
			assertThat(login(email, "senha-errada").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		}

		// bloqueada: até a senha CORRETA recebe 429 (o atacante não ganha
		// oráculo de senha durante o bloqueio) com Retry-After presente
		ResponseEntity<Map> bloqueada = login(email, "senha-correta-1");
		assertThat(bloqueada.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(bloqueada.getHeaders().getFirst("Retry-After")).isNotBlank();
	}

	@Test
	void sucessoZeraOContadorDeFalhas() {
		String email = "cliente-" + UUID.randomUUID() + "@x.com";
		authService.signup("Empresa", "Cliente", email, "senha-correta-1");

		// 2 falhas (abaixo do limite 3), depois sucesso
		login(email, "senha-errada");
		login(email, "senha-errada");
		assertThat(login(email, "senha-correta-1").getStatusCode()).isEqualTo(HttpStatus.OK);

		// contador zerado: 2 novas falhas ainda não bloqueiam
		login(email, "senha-errada");
		assertThat(login(email, "senha-errada").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(login(email, "senha-correta-1").getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void ipEstouradoRecebe429EmQualquerRotaDeAuth() {
		// esgota o limite de 8 requisições/min do IP de loopback
		for (int i = 0; i < 8; i++) {
			login("qualquer-" + UUID.randomUUID() + "@x.com", "x-senha-1");
		}
		ResponseEntity<Map> estourado = rest.postForEntity("/api/auth/signup",
				Map.of("tenantName", "E", "userName", "U", "email", "novo-" + UUID.randomUUID() + "@x.com",
						"password", "senha-forte-1"),
				Map.class);
		assertThat(estourado.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(estourado.getHeaders().getFirst("Retry-After")).isNotBlank();
	}

	private ResponseEntity<Map> login(String email, String password) {
		return rest.postForEntity("/api/auth/login", Map.of("email", email, "password", password), Map.class);
	}
}
