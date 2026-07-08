package com.omnichannel.tenant;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.omnichannel.TestcontainersConfiguration;

import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.time.SystemTimeProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fluxo 2FA completo pela API real: enrolamento, login em duas etapas,
 * código de recuperação de uso único e rejeição do token de desafio em
 * rotas protegidas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class TwoFactorAuthIntegrationTest {

	@Autowired
	TestRestTemplate rest;

	String email;
	String password = "senha-forte-1";
	String accessToken;

	@BeforeEach
	void signupSemMfa() {
		email = "mfa-" + UUID.randomUUID() + "@x.com";
		ResponseEntity<Map> signup = rest.postForEntity("/api/auth/signup",
				Map.of("tenantName", "Empresa MFA", "userName", "Dona", "email", email, "password", password),
				Map.class);
		accessToken = (String) signup.getBody().get("accessToken");
	}

	@Test
	void fluxoCompletoDeEnrolamentoLoginEmDuasEtapasERecoveryCode() {
		// 1. setup devolve segredo e URI otpauth
		ResponseEntity<Map> setup = post("/api/2fa/setup", accessToken, null);
		assertThat(setup.getStatusCode()).isEqualTo(HttpStatus.OK);
		String secret = (String) setup.getBody().get("secret");
		assertThat((String) setup.getBody().get("otpauthUri")).startsWith("otpauth://totp/");

		// 2. confirm com código correto ativa e entrega recovery codes
		ResponseEntity<Map> confirm = post("/api/2fa/confirm", accessToken, Map.of("code", totp(secret)));
		assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<String> recoveryCodes = (List<String>) confirm.getBody().get("recoveryCodes");
		assertThat(recoveryCodes).hasSize(8);

		// 3. login agora responde desafio, sem tokens
		ResponseEntity<Map> login = rest.postForEntity("/api/auth/login",
				Map.of("email", email, "password", password), Map.class);
		assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(login.getBody()).containsEntry("mfaRequired", true).doesNotContainKey("accessToken");
		String mfaToken = (String) login.getBody().get("mfaToken");

		// 3a. o token de desafio NÃO autentica rota protegida
		ResponseEntity<Map> desafioEmRotaProtegida = get("/api/me", mfaToken);
		assertThat(desafioEmRotaProtegida.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

		// 3b. código errado é rejeitado
		ResponseEntity<Map> codigoErrado = rest.postForEntity("/api/auth/2fa/verify",
				Map.of("mfaToken", mfaToken, "code", "000000"), Map.class);
		assertThat(codigoErrado.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

		// 3c. código correto troca desafio por tokens que funcionam
		ResponseEntity<Map> verified = rest.postForEntity("/api/auth/2fa/verify",
				Map.of("mfaToken", mfaToken, "code", totp(secret)), Map.class);
		assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(get("/api/me", (String) verified.getBody().get("accessToken")).getStatusCode())
			.isEqualTo(HttpStatus.OK);

		// 4. recovery code funciona UMA vez
		String recoveryCode = recoveryCodes.getFirst();
		String mfaToken2 = loginEsperandoDesafio();
		ResponseEntity<Map> comRecovery = rest.postForEntity("/api/auth/2fa/verify",
				Map.of("mfaToken", mfaToken2, "code", recoveryCode), Map.class);
		assertThat(comRecovery.getStatusCode()).isEqualTo(HttpStatus.OK);

		String mfaToken3 = loginEsperandoDesafio();
		ResponseEntity<Map> recoveryReutilizado = rest.postForEntity("/api/auth/2fa/verify",
				Map.of("mfaToken", mfaToken3, "code", recoveryCode), Map.class);
		assertThat(recoveryReutilizado.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void confirmComCodigoErradoNaoAtivaO2fa() {
		post("/api/2fa/setup", accessToken, null);
		ResponseEntity<Map> confirm = post("/api/2fa/confirm", accessToken, Map.of("code", "000000"));
		assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

		// login continua direto, sem desafio
		ResponseEntity<Map> login = rest.postForEntity("/api/auth/login",
				Map.of("email", email, "password", password), Map.class);
		assertThat(login.getBody()).containsKey("accessToken");
	}

	private String loginEsperandoDesafio() {
		ResponseEntity<Map> login = rest.postForEntity("/api/auth/login",
				Map.of("email", email, "password", password), Map.class);
		assertThat(login.getBody()).containsEntry("mfaRequired", true);
		return (String) login.getBody().get("mfaToken");
	}

	private String totp(String secret) {
		try {
			long bucket = new SystemTimeProvider().getTime() / 30;
			return new DefaultCodeGenerator().generate(secret, bucket);
		}
		catch (CodeGenerationException e) {
			throw new IllegalStateException(e);
		}
	}

	private ResponseEntity<Map> post(String path, String token, Object body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return rest.postForEntity(path, new HttpEntity<>(body, headers), Map.class);
	}

	private ResponseEntity<Map> get(String path, String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return rest.exchange(path, org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), Map.class);
	}
}
