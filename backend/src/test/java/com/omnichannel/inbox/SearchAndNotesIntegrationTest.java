package com.omnichannel.inbox;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.omnichannel.TestcontainersConfiguration;
import com.omnichannel.channels.ChannelType;
import com.omnichannel.channels.InboundMessageGateway;
import com.omnichannel.channels.InboundMessageReceived;
import com.omnichannel.channels.MessageContent;
import com.omnichannel.channels.domain.Channel;
import com.omnichannel.channels.domain.ChannelService;
import com.omnichannel.shared.tenancy.TenantContext;
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
import static org.awaitility.Awaitility.await;

/** Busca global (tsvector, RLS-scoped) e notas internas com autor. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SearchAndNotesIntegrationTest {

	@Autowired
	TestRestTemplate rest;

	@Autowired
	AuthService authService;

	@Autowired
	ChannelService channelService;

	@Autowired
	InboundMessageGateway gateway;

	@Autowired
	com.omnichannel.ai.AiSettingsService aiSettingsService;

	@Autowired
	org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

	String token;
	UUID tenantId;
	UUID userId;
	Channel channel;

	@BeforeEach
	void preparaTenantComConversa() {
		var tokens = authService.signup("Empresa Busca", "Agente", "busca-" + UUID.randomUUID() + "@x.com",
				"senha-forte-1");
		token = tokens.accessToken();
		var jwt = jwtDecoder.decode(token);
		tenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
		userId = UUID.fromString(jwt.getSubject());
		TenantContext.setTenantId(tenantId);
		aiSettingsService.update(tenantId, false, new java.math.BigDecimal("0.7"), null);
		channel = channelService.connect(tenantId, ChannelType.WEBCHAT, "Chat", Map.of());
		TenantContext.clear();

		gateway.receive(new InboundMessageReceived(tenantId, channel.getId(), ChannelType.WEBCHAT,
				"vis-busca-1", "Marcela Ferreira", "sb-" + UUID.randomUUID(),
				MessageContent.text("Preciso trocar o carregador com defeito"), Instant.now()));
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			ResponseEntity<Map> conversations = get("/api/conversations", Map.class);
			assertThat(conversations.getBody().get("totalElements")).isEqualTo(1);
		});
	}

	@Test
	void buscaEncontraMensagemPorPalavraEContatoPorNome() {
		ResponseEntity<List> porPalavra = get("/api/search?q=carregador", List.class);
		assertThat(porPalavra.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<Map<String, Object>> hits = porPalavra.getBody();
		assertThat(hits).hasSize(1);
		assertThat(hits.getFirst()).containsEntry("type", "MESSAGE");
		assertThat((String) hits.getFirst().get("snippet")).contains("carregador");
		assertThat(hits.getFirst().get("conversationId")).isNotNull();

		ResponseEntity<List> porNome = get("/api/search?q=Marcela", List.class);
		assertThat((List<Map>) porNome.getBody()).extracting(hit -> hit.get("type")).contains("CONTACT");

		// stemming do português: "trocar" indexado acha "troca"
		ResponseEntity<List> flexionado = get("/api/search?q=troca", List.class);
		assertThat((List<Map>) flexionado.getBody()).isNotEmpty();
	}

	@Test
	void buscaNaoVazaEntreTenantsEQueryCurtaVoltaVazia() {
		String outroToken = authService
			.signup("Outra Empresa", "Bruno", "outra-busca-" + UUID.randomUUID() + "@x.com", "senha-forte-2")
			.accessToken();
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(outroToken);
		ResponseEntity<List> outro = rest.exchange("/api/search?q=carregador", HttpMethod.GET,
				new HttpEntity<>(headers), List.class);
		assertThat((List<?>) outro.getBody()).isEmpty();

		assertThat((List<?>) get("/api/search?q=a", List.class).getBody()).isEmpty();
	}

	@Test
	void notaInternaEAdicionadaComAutorEListadaEmOrdem() {
		ResponseEntity<Map> conversations = get("/api/conversations", Map.class);
		String conversationId = (String) ((List<Map>) conversations.getBody().get("content")).getFirst().get("id");

		ResponseEntity<Map> created = post("/api/conversations/" + conversationId + "/notes",
				Map.of("text", "Cliente VIP — priorizar troca sem custo"));
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(created.getBody()).containsEntry("authorUserId", userId.toString());

		post("/api/conversations/" + conversationId + "/notes", Map.of("text", "Encaminhei para o estoque"));

		ResponseEntity<List> notes = get("/api/conversations/" + conversationId + "/notes", List.class);
		List<Map<String, Object>> list = notes.getBody();
		assertThat(list).hasSize(2);
		assertThat((String) list.get(0).get("text")).contains("VIP");
		assertThat((String) list.get(1).get("text")).contains("estoque");
	}

	@Test
	void notaEmConversaDeOutroTenantRetorna404() {
		ResponseEntity<Map> conversations = get("/api/conversations", Map.class);
		String conversationId = (String) ((List<Map>) conversations.getBody().get("content")).getFirst().get("id");

		String outroToken = authService
			.signup("Espiã Ltda", "Espiã", "espia-" + UUID.randomUUID() + "@x.com", "senha-forte-2")
			.accessToken();
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(outroToken);
		ResponseEntity<Map> negado = rest.exchange("/api/conversations/" + conversationId + "/notes",
				HttpMethod.POST, new HttpEntity<>(Map.of("text", "bisbilhotando"), headers), Map.class);
		assertThat(negado.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	private <T> ResponseEntity<T> get(String path, Class<T> type) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), type);
	}

	private ResponseEntity<Map> post(String path, Object body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
	}
}
