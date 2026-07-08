package com.omnichannel;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** O contrato OpenAPI é acessível sem token e cobre as rotas existentes. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OpenApiContractTest {

	@Autowired
	TestRestTemplate rest;

	@Test
	void contratoExpoeAsRotasDaApi() {
		ResponseEntity<Map> contract = rest.getForEntity("/v3/api-docs", Map.class);
		assertThat(contract.getStatusCode()).isEqualTo(HttpStatus.OK);

		Map<String, Object> paths = (Map<String, Object>) contract.getBody().get("paths");
		assertThat(paths).containsKeys("/api/auth/signup", "/api/auth/login", "/api/auth/refresh", "/api/me",
				"/api/channels", "/api/channels/{id}/disable");
	}
}
