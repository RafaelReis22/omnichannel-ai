package com.omnichannel.inbox;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.omnichannel.TestcontainersConfiguration;
import com.omnichannel.channels.ChannelType;
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

/**
 * Conversa real visitante↔agente pelo canal de webchat: o visitante posta
 * sem autenticação, o agente vê e responde no inbox, o visitante recebe a
 * resposta via polling.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class WebchatFlowIntegrationTest {

	@Autowired
	TestRestTemplate rest;

	@Autowired
	AuthService authService;

	@Autowired
	com.omnichannel.ai.AiSettingsService aiSettingsService;

	@Autowired
	ChannelService channelService;

	@Autowired
	org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

	String agentToken;
	UUID tenantId;
	Channel channel;
	String visitorId;

	@BeforeEach
	void preparaTenantECanal() {
		var tokens = authService.signup("Loja Webchat", "Atendente", "webchat-" + UUID.randomUUID() + "@x.com",
				"senha-forte-1");
		agentToken = tokens.accessToken();
		tenantId = UUID.fromString(jwtDecoder.decode(agentToken).getClaimAsString("tenant_id"));
		TenantContext.setTenantId(tenantId);
		channel = channelService.connect(tenantId, ChannelType.WEBCHAT, "Chat do Site", Map.of());
		aiSettingsService.update(tenantId, false, new java.math.BigDecimal("0.7"), null); // IA fora destes testes
		TenantContext.clear();
		visitorId = "vis-" + UUID.randomUUID();
	}

	@Test
	void visitanteEAgenteConversamDePontaAPonta() {
		// visitante manda mensagem sem nenhum token
		ResponseEntity<Void> sent = rest.postForEntity(webchatUrl("/messages"),
				Map.of("visitorId", visitorId, "visitorName", "Visitante Maria", "text", "Oi! Vocês entregam hoje?",
						"clientMessageId", "c-1"),
				Void.class);
		assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

		// agente vê a conversa no inbox
		await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
			ResponseEntity<Map> conversations = agentGet("/api/conversations");
			assertThat(conversations.getBody().get("totalElements")).isEqualTo(1);
		});
		Map conversation = ((List<Map>) agentGet("/api/conversations").getBody().get("content")).getFirst();
		String conversationId = (String) conversation.get("id");

		// agente responde
		ResponseEntity<Map> reply = agentPost("/api/conversations/" + conversationId + "/reply",
				Map.of("kind", "TEXT", "text", "Entregamos sim! Até as 18h."));
		assertThat(reply.getStatusCode()).isEqualTo(HttpStatus.CREATED);

		// visitante recebe a resposta via polling, em ordem
		ResponseEntity<List> thread = rest.getForEntity(webchatUrl("/messages?visitorId=" + visitorId), List.class);
		assertThat(thread.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<Map<String, Object>> messages = thread.getBody();
		assertThat(messages).hasSize(2);
		assertThat(messages.get(0)).containsEntry("sender", "CONTACT").containsEntry("text",
				"Oi! Vocês entregam hoje?");
		assertThat(messages.get(1)).containsEntry("sender", "AGENT").containsEntry("text",
				"Entregamos sim! Até as 18h.");

		// polling incremental: after = createdAt da primeira → só a resposta
		String after = (String) messages.get(0).get("createdAt");
		ResponseEntity<List> incremental = rest
			.getForEntity(webchatUrl("/messages?visitorId=" + visitorId + "&after=" + after), List.class);
		assertThat((List<Map>) incremental.getBody()).hasSize(1);
	}

	@Test
	void reenvioComMesmoClientMessageIdNaoDuplica() {
		Map<String, String> body = Map.of("visitorId", visitorId, "text", "mensagem única", "clientMessageId",
				"retry-1");
		rest.postForEntity(webchatUrl("/messages"), body, Void.class);
		rest.postForEntity(webchatUrl("/messages"), body, Void.class);

		await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
			ResponseEntity<List> thread = rest.getForEntity(webchatUrl("/messages?visitorId=" + visitorId),
					List.class);
			assertThat((List<Map>) thread.getBody()).hasSize(1);
		});
	}

	@Test
	void canalInexistenteDesabilitadoOuDeOutroTipoRetorna404() {
		ResponseEntity<Map> inexistente = rest.postForEntity(
				"/api/webchat/" + UUID.randomUUID() + "/messages",
				Map.of("visitorId", visitorId, "text", "oi"), Map.class);
		assertThat(inexistente.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

		TenantContext.setTenantId(tenantId);
		Channel email = channelService.connect(tenantId, ChannelType.EMAIL, "Email", Map.of("k", "v"));
		channelService.disable(channel.getId());
		TenantContext.clear();

		ResponseEntity<Map> tipoErrado = rest.postForEntity("/api/webchat/" + email.getId() + "/messages",
				Map.of("visitorId", visitorId, "text", "oi"), Map.class);
		assertThat(tipoErrado.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

		ResponseEntity<Map> desabilitado = rest.postForEntity(webchatUrl("/messages"),
				Map.of("visitorId", visitorId, "text", "oi"), Map.class);
		assertThat(desabilitado.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void visitanteNaoEnxergaThreadDeOutroVisitante() {
		rest.postForEntity(webchatUrl("/messages"), Map.of("visitorId", visitorId, "text", "meu segredo"),
				Void.class);
		await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
			ResponseEntity<List> own = rest.getForEntity(webchatUrl("/messages?visitorId=" + visitorId), List.class);
			assertThat((List<Map>) own.getBody()).hasSize(1);
		});

		ResponseEntity<List> other = rest.getForEntity(webchatUrl("/messages?visitorId=outro-visitante"),
				List.class);
		assertThat((List<Map>) other.getBody()).isEmpty();
	}

	private String webchatUrl(String suffix) {
		return "/api/webchat/" + channel.getId() + suffix;
	}

	private ResponseEntity<Map> agentGet(String path) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(agentToken);
		return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
	}

	private ResponseEntity<Map> agentPost(String path, Object body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(agentToken);
		return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
	}
}
