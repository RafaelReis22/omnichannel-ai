package com.omnichannel.analytics;

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
import com.omnichannel.inbox.InboxService;
import com.omnichannel.inbox.domain.ConversationRepository;
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
 * Visão geral operacional calculada de dados reais: conversas via gateway,
 * resposta autônoma da IA (fake) alimentando o livro-razão, resolução por
 * agente — e RLS isolando os números entre tenants.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AnalyticsIntegrationTest {

	@Autowired
	TestRestTemplate rest;

	@Autowired
	AuthService authService;

	@Autowired
	ChannelService channelService;

	@Autowired
	InboundMessageGateway gateway;

	@Autowired
	ConversationRepository conversationRepository;

	@Autowired
	InboxService inboxService;

	@Autowired
	org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

	String agentToken;
	UUID tenantId;
	UUID agentUserId;
	Channel channel;

	@BeforeEach
	void preparaTenant() {
		TestcontainersConfiguration.FakeAnswerGenerator.reset();
		var tokens = authService.signup("Loja Métricas", "Gestora", "metrics-" + UUID.randomUUID() + "@x.com",
				"senha-forte-1");
		agentToken = tokens.accessToken();
		var jwt = jwtDecoder.decode(agentToken);
		tenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
		agentUserId = UUID.fromString(jwt.getSubject());
		TenantContext.setTenantId(tenantId);
		channel = channelService.connect(tenantId, ChannelType.WEBCHAT, "Chat", Map.of());
		TenantContext.clear();
	}

	private HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return headers;
	}

	private void visitorSays(String visitorId, String text) {
		gateway.receive(new InboundMessageReceived(tenantId, channel.getId(), ChannelType.WEBCHAT, visitorId,
				"Visitante " + visitorId, visitorId + "-" + UUID.randomUUID(), MessageContent.text(text),
				Instant.now()));
	}

	@Test
	void overviewContaConversasIaEResolucaoEOutroTenantVeZero() {
		// duas conversas; a IA (fake, confiança 0.95) responde as duas sozinha
		visitorSays("v1", "Qual o horário de vocês?");
		visitorSays("v2", "Vocês entregam no centro?");
		await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
			TenantContext.setTenantId(tenantId);
			try {
				assertThat(conversationRepository.count()).isEqualTo(2);
				assertThat(conversationRepository.findAll())
					.allMatch(c -> c.getStatus().name().equals("WITH_AI"));
			}
			finally {
				TenantContext.clear();
			}
		});

		// agente resolve uma
		TenantContext.setTenantId(tenantId);
		UUID resolvedId = conversationRepository.findAll().getFirst().getId();
		inboxService.resolve(resolvedId);
		TenantContext.clear();

		ResponseEntity<Map> overview = rest.exchange("/api/analytics/overview?days=7", HttpMethod.GET,
				new HttpEntity<>(bearer(agentToken)), Map.class);
		assertThat(overview.getStatusCode()).isEqualTo(HttpStatus.OK);
		Map<String, Object> body = overview.getBody();
		assertThat(body).containsEntry("newConversations", 2)
			.containsEntry("inboundMessages", 2)
			.containsEntry("aiAnswered", 2)
			.containsEntry("aiHandoffs", 0)
			.containsEntry("resolvedConversations", 1);
		assertThat((Number) body.get("medianFirstResponseSeconds")).isNotNull();

		List<Map<String, Object>> perDay = (List<Map<String, Object>>) body.get("conversationsPerDay");
		assertThat(perDay).hasSize(1);
		assertThat(perDay.getFirst()).containsEntry("count", 2);

		List<Map<String, Object>> byChannel = (List<Map<String, Object>>) body.get("byChannelType");
		assertThat(byChannel).hasSize(1);
		assertThat(byChannel.getFirst()).containsEntry("label", "WEBCHAT").containsEntry("count", 2);

		// outro tenant enxerga tudo zerado (RLS na própria query)
		var otherTokens = authService.signup("Outra Empresa", "Outro", "outro-" + UUID.randomUUID() + "@x.com",
				"senha-forte-1");
		ResponseEntity<Map> otherOverview = rest.exchange("/api/analytics/overview?days=7", HttpMethod.GET,
				new HttpEntity<>(bearer(otherTokens.accessToken())), Map.class);
		assertThat(otherOverview.getBody()).containsEntry("newConversations", 0)
			.containsEntry("aiAnswered", 0)
			.containsEntry("inboundMessages", 0);
	}
}
