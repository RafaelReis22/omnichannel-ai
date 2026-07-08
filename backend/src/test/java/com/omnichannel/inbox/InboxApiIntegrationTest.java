package com.omnichannel.inbox;

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

/**
 * API do inbox de ponta a ponta sobre o adapter real de webchat (envio =
 * persistência; a visão do visitante é coberta em WebchatFlowIntegrationTest).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class InboxApiIntegrationTest {

	@Autowired
	TestRestTemplate rest;

	@Autowired
	AuthService authService;

	@Autowired
	com.omnichannel.ai.AiSettingsService aiSettingsService;

	@Autowired
	ChannelService channelService;

	@Autowired
	InboundMessageGateway gateway;

	@Autowired
	org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

	String adminToken;
	UUID tenantId;
	UUID adminUserId;
	Channel channel;

	@BeforeEach
	void preparaTenantCanalEConversa() {
		var tokens = authService.signup("Empresa API Inbox", "Admin", "api-inbox-" + UUID.randomUUID() + "@x.com",
				"senha-forte-1");
		adminToken = tokens.accessToken();
		var jwt = jwtDecoder.decode(adminToken);
		tenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
		adminUserId = UUID.fromString(jwt.getSubject());

		TenantContext.setTenantId(tenantId);
		channel = channelService.connect(tenantId, ChannelType.WEBCHAT, "Chat", Map.of("k", "v"));
		aiSettingsService.update(tenantId, false, new java.math.BigDecimal("0.7"), null); // IA fora destes testes
		TenantContext.clear();

		gateway.receive(new InboundMessageReceived(tenantId, channel.getId(), ChannelType.WEBCHAT, "cliente-1",
				"Cliente Um", "api-m-" + UUID.randomUUID(), MessageContent.text("Preciso de ajuda"), Instant.now()));
		await().atMost(java.time.Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(listConversations().getBody().get("totalElements")).isEqualTo(1));
	}

	@Test
	void listarAtribuirEtiquetarResolverEConsultarHistorico() {
		Map conversation = firstConversation();
		String conversationId = (String) conversation.get("id");
		assertThat(conversation).containsEntry("status", "OPEN").containsEntry("lastMessagePreview",
				"Preciso de ajuda");

		// atribuir → WITH_HUMAN
		ResponseEntity<Map> assigned = post("/api/conversations/" + conversationId + "/assign",
				Map.of("userId", adminUserId.toString()));
		assertThat(assigned.getBody()).containsEntry("status", "WITH_HUMAN")
			.containsEntry("assigneeUserId", adminUserId.toString());

		// filtrar por atribuído
		ResponseEntity<Map> filtered = get(
				"/api/conversations?status=WITH_HUMAN&assigneeUserId=" + adminUserId);
		assertThat(filtered.getBody().get("totalElements")).isEqualTo(1);

		// etiquetas
		ResponseEntity<Map> tagged = put("/api/conversations/" + conversationId + "/tags",
				Map.of("tags", List.of("vip", "orçamento")));
		assertThat((List<String>) tagged.getBody().get("tags")).containsExactly("vip", "orçamento");

		// histórico consolidado do contato
		String contactId = (String) conversation.get("contactId");
		ResponseEntity<Map> contact = get("/api/contacts/" + contactId);
		assertThat(contact.getBody()).containsEntry("name", "Cliente Um");
		assertThat((List<Map>) contact.getBody().get("identities")).hasSize(1);

		// resolver
		ResponseEntity<Map> resolved = post("/api/conversations/" + conversationId + "/resolve", null);
		assertThat(resolved.getBody()).containsEntry("status", "RESOLVED");
	}

	@Test
	void respostaDeAgentePersisteEEnviaPeloCanal() {
		String conversationId = (String) firstConversation().get("id");

		ResponseEntity<Map> reply = post("/api/conversations/" + conversationId + "/reply",
				Map.of("kind", "TEXT", "text", "Claro! Como posso ajudar?"));
		assertThat(reply.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(reply.getBody()).containsEntry("sender", "AGENT")
			.containsEntry("senderUserId", adminUserId.toString());

		// está no histórico da conversa (para webchat, entrega = persistência)
		ResponseEntity<Map> messages = get("/api/conversations/" + conversationId + "/messages");
		assertThat((List<Map>) messages.getBody().get("content")).hasSize(2);
	}

	@Test
	void conversaDeOutroTenantEInvisivel() {
		String conversationId = (String) firstConversation().get("id");

		String outroToken = authService
			.signup("Outra Empresa", "Bruno", "outro-inbox-" + UUID.randomUUID() + "@x.com", "senha-forte-2")
			.accessToken();

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(outroToken);
		ResponseEntity<Map> lista = rest.exchange("/api/conversations", HttpMethod.GET, new HttpEntity<>(headers),
				Map.class);
		assertThat(lista.getBody().get("totalElements")).isEqualTo(0);

		ResponseEntity<Map> detalhe = rest.exchange("/api/conversations/" + conversationId, HttpMethod.GET,
				new HttpEntity<>(headers), Map.class);
		assertThat(detalhe.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	private Map firstConversation() {
		return ((List<Map>) listConversations().getBody().get("content")).getFirst();
	}

	private ResponseEntity<Map> listConversations() {
		return get("/api/conversations");
	}

	private ResponseEntity<Map> get(String path) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(adminToken);
		return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
	}

	private ResponseEntity<Map> post(String path, Object body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(adminToken);
		return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
	}

	private ResponseEntity<Map> put(String path, Object body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(adminToken);
		return rest.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, headers), Map.class);
	}
}
