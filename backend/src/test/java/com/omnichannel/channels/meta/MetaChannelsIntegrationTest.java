package com.omnichannel.channels.meta;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;

import com.omnichannel.TestcontainersConfiguration;
import com.omnichannel.channels.meta.MetaSignatures;
import com.omnichannel.inbox.InboxService;
import com.omnichannel.inbox.domain.ConversationRepository;
import com.omnichannel.shared.tenancy.TenantContext;
import com.omnichannel.tenant.AuthService;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Canais Meta (Messenger e Instagram) offline: verificação de endpoint
 * (hub.challenge), POST com assinatura HMAC real calculada no teste, e
 * envio capturado por um Graph API simulado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class MetaChannelsIntegrationTest {

	static final List<String> GRAPH_REQUESTS = new CopyOnWriteArrayList<>();
	static HttpServer fakeGraph;

	@Autowired
	TestRestTemplate rest;

	@Autowired
	AuthService authService;

	@Autowired
	ConversationRepository conversationRepository;

	@Autowired
	InboxService inboxService;

	@Autowired
	com.omnichannel.ai.AiSettingsService aiSettingsService;

	@Autowired
	org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

	String adminToken;
	UUID tenantId;
	UUID agentUserId;

	@BeforeEach
	void preparaTenant() throws IOException {
		GRAPH_REQUESTS.clear();
		if (fakeGraph == null) {
			fakeGraph = HttpServer.create(new InetSocketAddress(0), 0);
			fakeGraph.createContext("/", exchange -> {
				GRAPH_REQUESTS.add(exchange.getRequestURI().toString() + "|"
						+ new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
				exchange.sendResponseHeaders(200, 2);
				exchange.getResponseBody().write("{}".getBytes(StandardCharsets.UTF_8));
				exchange.close();
			});
			fakeGraph.start();
		}
		var tokens = authService.signup("Empresa Meta", "Admin", "meta-" + UUID.randomUUID() + "@x.com",
				"senha-forte-1");
		adminToken = tokens.accessToken();
		var jwt = jwtDecoder.decode(adminToken);
		tenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
		agentUserId = UUID.fromString(jwt.getSubject());
		TenantContext.setTenantId(tenantId);
		aiSettingsService.update(tenantId, false, new java.math.BigDecimal("0.7"), null);
		TenantContext.clear();
	}

	@AfterEach
	void limpaContexto() {
		TenantContext.clear();
	}

	private record ConnectedMeta(String channelId, String webhookPath, String verifyToken) {
	}

	private ConnectedMeta connect(String type, String appSecret) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(adminToken);
		ResponseEntity<Map> created = rest.exchange("/api/channels", HttpMethod.POST,
				new HttpEntity<>(Map.of("type", type, "name", type + " Oficial", "credentials",
						Map.of("app_secret", appSecret, "access_token", "token-da-pagina", "base_url",
								"http://127.0.0.1:" + fakeGraph.getAddress().getPort())),
						headers),
				Map.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return new ConnectedMeta((String) created.getBody().get("id"),
				(String) created.getBody().get("webhookPath"), (String) created.getBody().get("verifyToken"));
	}

	private String messengerPayload(String senderId, String mid, String text) {
		return """
				{"object":"page","entry":[{"id":"pag-1","time":1720000000,"messaging":[{"sender":{"id":"%s"},
				"recipient":{"id":"pag-1"},"timestamp":1720000000,
				"message":{"mid":"%s","text":"%s"}}]}]}""".formatted(senderId, mid, text);
	}

	private ResponseEntity<Void> postSigned(String path, String body, String appSecret) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("X-Hub-Signature-256", "sha256=" + MetaSignatures.hmacSha256(appSecret, body));
		return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Void.class);
	}

	@Test
	void verificacaoDeEndpointEcoaChallengeSoComTokenCerto() {
		ConnectedMeta channel = connect("MESSENGER", "segredo-app-1");

		ResponseEntity<String> ok = rest.getForEntity(channel.webhookPath()
				+ "?hub.mode=subscribe&hub.verify_token=" + channel.verifyToken() + "&hub.challenge=desafio-123",
				String.class);
		assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(ok.getBody()).isEqualTo("desafio-123");

		ResponseEntity<String> negado = rest.getForEntity(channel.webhookPath()
				+ "?hub.mode=subscribe&hub.verify_token=errado&hub.challenge=x", String.class);
		assertThat(negado.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void messengerAssinadoIngereEAgenteRespondePeloGraph() {
		ConnectedMeta channel = connect("MESSENGER", "segredo-app-2");
		String body = messengerPayload("psid-777", "mid.abc", "Olá pelo Messenger!");

		assertThat(postSigned(channel.webhookPath(), body, "segredo-app-2").getStatusCode())
			.isEqualTo(HttpStatus.OK);

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			TenantContext.setTenantId(tenantId);
			try {
				assertThat(conversationRepository.count()).isEqualTo(1);
				assertThat(conversationRepository.findAll().getFirst().getLastMessagePreview())
					.isEqualTo("Olá pelo Messenger!");
			}
			finally {
				TenantContext.clear();
			}
		});

		TenantContext.setTenantId(tenantId);
		UUID conversationId = conversationRepository.findAll().getFirst().getId();
		inboxService.replyAsAgent(conversationId, agentUserId,
				com.omnichannel.channels.MessageContent.text("Oi! Como posso ajudar?"));
		TenantContext.clear();

		assertThat(GRAPH_REQUESTS).hasSize(1);
		String sent = GRAPH_REQUESTS.getFirst();
		assertThat(sent).contains("/me/messages").contains("access_token=token-da-pagina")
			.contains("\"id\":\"psid-777\"").contains("Como posso ajudar?");
	}

	@Test
	void instagramAssinadoIngereComObjectInstagram() {
		ConnectedMeta channel = connect("INSTAGRAM", "segredo-app-3");
		String body = """
				{"object":"instagram","entry":[{"id":"ig-1","time":1720000000,"messaging":[{"sender":{"id":"ig-user-9"},
				"recipient":{"id":"ig-1"},"timestamp":1720000000,
				"message":{"mid":"mid.ig1","text":"Vi no story de vocês!"}}]}]}""";

		assertThat(postSigned(channel.webhookPath(), body, "segredo-app-3").getStatusCode())
			.isEqualTo(HttpStatus.OK);

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			TenantContext.setTenantId(tenantId);
			try {
				assertThat(conversationRepository.count()).isEqualTo(1);
			}
			finally {
				TenantContext.clear();
			}
		});
	}

	@Test
	void assinaturaInvalidaOuAusenteRecebe403ENadaEntra() {
		ConnectedMeta channel = connect("MESSENGER", "segredo-app-4");
		String body = messengerPayload("psid-1", "mid.x", "forjado");

		assertThat(postSigned(channel.webhookPath(), body, "segredo-errado").getStatusCode())
			.isEqualTo(HttpStatus.FORBIDDEN);

		HttpHeaders semAssinatura = new HttpHeaders();
		semAssinatura.setContentType(MediaType.APPLICATION_JSON);
		assertThat(rest.exchange(channel.webhookPath(), HttpMethod.POST, new HttpEntity<>(body, semAssinatura),
				Void.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		await().during(Duration.ofSeconds(3)).untilAsserted(() -> {
			TenantContext.setTenantId(tenantId);
			try {
				assertThat(conversationRepository.count()).isZero();
			}
			finally {
				TenantContext.clear();
			}
		});
	}

	@Test
	void ecoDaPropriaPaginaEIgnorado() {
		ConnectedMeta channel = connect("MESSENGER", "segredo-app-5");
		String echo = """
				{"object":"page","entry":[{"id":"pag-1","messaging":[{"sender":{"id":"pag-1"},
				"message":{"mid":"mid.echo","is_echo":true,"text":"resposta nossa ecoada"}}]}]}""";

		assertThat(postSigned(channel.webhookPath(), echo, "segredo-app-5").getStatusCode())
			.isEqualTo(HttpStatus.OK);

		await().during(Duration.ofSeconds(3)).untilAsserted(() -> {
			TenantContext.setTenantId(tenantId);
			try {
				assertThat(conversationRepository.count()).isZero();
			}
			finally {
				TenantContext.clear();
			}
		});
	}
}
