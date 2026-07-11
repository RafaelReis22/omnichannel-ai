package com.omnichannel.channels.whatsapp;

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
import com.omnichannel.channels.ChannelType;
import com.omnichannel.inbox.InboxService;
import com.omnichannel.inbox.domain.ContactRepository;
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
 * Adapter de WhatsApp de ponta a ponta, offline: o webhook recebe um
 * payload real do formato Cloud API (encaminhado pelo BSP) e o envio é
 * capturado por um servidor HTTP local fazendo o papel da 360dialog.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class WhatsAppChannelIntegrationTest {

	static final List<CapturedRequest> BSP_REQUESTS = new CopyOnWriteArrayList<>();
	static final byte[] PNG_BYTES = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 9, 8, 7 };

	record CapturedRequest(String path, String apiKey, String body) {
	}

	static HttpServer fakeBsp;

	@Autowired
	TestRestTemplate rest;

	@Autowired
	AuthService authService;

	@Autowired
	ContactRepository contactRepository;

	@Autowired
	ConversationRepository conversationRepository;

	@Autowired
	InboxService inboxService;

	@Autowired
	com.omnichannel.ai.AiSettingsService aiSettingsService;

	@Autowired
	com.omnichannel.shared.storage.StorageService storageService;

	@Autowired
	org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

	String adminToken;
	UUID tenantId;
	UUID agentUserId;
	String channelId;
	String webhookPath;

	@BeforeEach
	void preparaTenantECanalWhatsApp() throws IOException {
		BSP_REQUESTS.clear();
		if (fakeBsp == null) {
			fakeBsp = HttpServer.create(new InetSocketAddress(0), 0);
			fakeBsp.createContext("/", exchange -> {
				String path = exchange.getRequestURI().getPath();
				String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
				String auth = exchange.getRequestHeaders().getFirst("Authorization");
				String apiKey = exchange.getRequestHeaders().getFirst("D360-API-KEY");
				BSP_REQUESTS.add(new CapturedRequest(path, apiKey != null ? apiKey : auth, body));
				byte[] response;
				if (path.startsWith("/media-")) {
					// metadado de mídia da Cloud API: aponta para o "CDN" local
					response = ("{\"url\":\"http://127.0.0.1:" + fakeBsp.getAddress().getPort() + "/lookaside"
							+ path + "\",\"mime_type\":\"image/png\"}").getBytes(StandardCharsets.UTF_8);
					exchange.getResponseHeaders().set("Content-Type", "application/json");
				}
				else if (path.startsWith("/lookaside/")) {
					response = PNG_BYTES;
				}
				else {
					response = "{}".getBytes(StandardCharsets.UTF_8);
					exchange.getResponseHeaders().set("Content-Type", "application/json");
				}
				exchange.sendResponseHeaders(200, response.length);
				exchange.getResponseBody().write(response);
				exchange.close();
			});
			fakeBsp.start();
		}

		var tokens = authService.signup("Empresa Zap", "Admin", "zap-" + UUID.randomUUID() + "@x.com",
				"senha-forte-1");
		adminToken = tokens.accessToken();
		var jwt = jwtDecoder.decode(adminToken);
		tenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
		agentUserId = UUID.fromString(jwt.getSubject());
		TenantContext.setTenantId(tenantId);
		aiSettingsService.update(tenantId, false, new java.math.BigDecimal("0.7"), null);
		TenantContext.clear();

		// conecta via API: o webhookPath (com o token secreto) só aparece aqui
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(adminToken);
		ResponseEntity<Map> created = rest.exchange("/api/channels", HttpMethod.POST,
				new HttpEntity<>(Map.of("type", "WHATSAPP", "name", "WhatsApp Loja", "credentials",
						Map.of("api_key", "chave-do-bsp", "base_url",
								"http://127.0.0.1:" + fakeBsp.getAddress().getPort())),
						headers),
				Map.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		channelId = (String) created.getBody().get("id");
		webhookPath = (String) created.getBody().get("webhookPath");
		assertThat(webhookPath).startsWith("/api/whatsapp/webhook/" + channelId + "/");
	}

	@AfterEach
	void limpaContexto() {
		TenantContext.clear();
	}

	private String cloudApiPayload(String from, String messageId, String text) {
		return """
				{"object":"whatsapp_business_account","entry":[{"id":"123","changes":[{"field":"messages",
				"value":{"messaging_product":"whatsapp",
				"contacts":[{"profile":{"name":"Cliente Zap"},"wa_id":"%s"}],
				"messages":[{"from":"%s","id":"%s","timestamp":"1720000000","type":"text",
				"text":{"body":"%s"}}]}}]}]}""".formatted(from, from, messageId, text);
	}

	@Test
	void webhookIngereMensagemEAgenteRespondePeloBsp() {
		HttpHeaders json = new HttpHeaders();
		json.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<Void> webhook = rest.exchange(webhookPath, HttpMethod.POST,
				new HttpEntity<>(cloudApiPayload("5551999990000", "wamid.teste-1", "Oi! Tem no tamanho M?"), json),
				Void.class);
		assertThat(webhook.getStatusCode()).isEqualTo(HttpStatus.OK);

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			TenantContext.setTenantId(tenantId);
			try {
				assertThat(conversationRepository.count()).isEqualTo(1);
				var conversation = conversationRepository.findAll().getFirst();
				assertThat(conversation.getLastMessagePreview()).isEqualTo("Oi! Tem no tamanho M?");
				assertThat(contactRepository.findById(conversation.getContactId()).orElseThrow().getName())
					.isEqualTo("Cliente Zap");
			}
			finally {
				TenantContext.clear();
			}
		});

		// agente responde → requisição chega no "BSP" com a chave e o destino certos
		TenantContext.setTenantId(tenantId);
		UUID conversationId = conversationRepository.findAll().getFirst().getId();
		inboxService.replyAsAgent(conversationId, agentUserId,
				com.omnichannel.channels.MessageContent.text("Temos sim! Posso separar um para você?"));
		TenantContext.clear();

		assertThat(BSP_REQUESTS).hasSize(1);
		CapturedRequest sent = BSP_REQUESTS.getFirst();
		assertThat(sent.path()).isEqualTo("/messages");
		assertThat(sent.apiKey()).isEqualTo("chave-do-bsp");
		assertThat(sent.body()).contains("\"to\":\"5551999990000\"").contains("Posso separar um para você?");
	}

	private record CloudChannel(String channelId, String webhookPath, String verifyToken) {
	}

	private CloudChannel connectCloudChannel() {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(adminToken);
		ResponseEntity<Map> created = rest.exchange("/api/channels", HttpMethod.POST,
				new HttpEntity<>(Map.of("type", "WHATSAPP", "name", "Zap Cloud", "credentials",
						Map.of("phone_number_id", "555000111", "access_token", "token-meta",
								"app_secret", "segredo-meta",
								"base_url", "http://127.0.0.1:" + fakeBsp.getAddress().getPort())),
						headers),
				Map.class);
		return new CloudChannel((String) created.getBody().get("id"),
				(String) created.getBody().get("webhookPath"), (String) created.getBody().get("verifyToken"));
	}

	@Test
	void cloudApiVerificaEndpointIngereAssinadoERespondePorPhoneNumberId() {
		CloudChannel channel = connectCloudChannel();
		assertThat(channel.verifyToken()).isNotBlank();

		// GET de verificação da Meta: verify token é o token da URL
		ResponseEntity<String> challenge = rest.getForEntity(channel.webhookPath()
				+ "?hub.mode=subscribe&hub.verify_token=" + channel.verifyToken() + "&hub.challenge=meta-123",
				String.class);
		assertThat(challenge.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(challenge.getBody()).isEqualTo("meta-123");

		// POST sem assinatura é recusado (canal tem app_secret)
		String body = cloudApiPayload("5551977770000", "wamid.cloud-1", "Oi pela Cloud API!");
		HttpHeaders json = new HttpHeaders();
		json.setContentType(MediaType.APPLICATION_JSON);
		assertThat(rest.exchange(channel.webhookPath(), HttpMethod.POST, new HttpEntity<>(body, json), Void.class)
			.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		// POST assinado é ingerido
		HttpHeaders signed = new HttpHeaders();
		signed.setContentType(MediaType.APPLICATION_JSON);
		signed.set("X-Hub-Signature-256", "sha256="
				+ MetaSignatures.hmacSha256("segredo-meta", body));
		assertThat(rest.exchange(channel.webhookPath(), HttpMethod.POST, new HttpEntity<>(body, signed), Void.class)
			.getStatusCode()).isEqualTo(HttpStatus.OK);

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			TenantContext.setTenantId(tenantId);
			try {
				assertThat(conversationRepository.count()).isEqualTo(1);
			}
			finally {
				TenantContext.clear();
			}
		});

		// resposta sai pela Cloud API: /{phone_number_id}/messages com Bearer
		TenantContext.setTenantId(tenantId);
		UUID conversationId = conversationRepository.findAll().getFirst().getId();
		inboxService.replyAsAgent(conversationId, agentUserId,
				com.omnichannel.channels.MessageContent.text("Recebido pela Cloud API!"));
		TenantContext.clear();

		assertThat(BSP_REQUESTS).hasSize(1);
		CapturedRequest sent = BSP_REQUESTS.getFirst();
		assertThat(sent.path()).isEqualTo("/555000111/messages");
		assertThat(sent.body()).contains("\"to\":\"5551977770000\"");
	}

	@Test
	void cloudApiIngereImagemEAgenteRespondeComMidiaPorLink() {
		CloudChannel channel = connectCloudChannel();

		// visitante manda uma imagem com legenda
		String body = """
				{"object":"whatsapp_business_account","entry":[{"id":"123","changes":[{"field":"messages",
				"value":{"messaging_product":"whatsapp",
				"contacts":[{"profile":{"name":"Cliente Foto"},"wa_id":"5551966660000"}],
				"messages":[{"from":"5551966660000","id":"wamid.img-1","timestamp":"1720000000","type":"image",
				"image":{"id":"media-img-1","mime_type":"image/png","caption":"Serve nesse espaço?"}}]}}]}]}""";
		HttpHeaders signed = new HttpHeaders();
		signed.setContentType(MediaType.APPLICATION_JSON);
		signed.set("X-Hub-Signature-256", "sha256=" + MetaSignatures.hmacSha256("segredo-meta", body));
		assertThat(rest.exchange(channel.webhookPath(), HttpMethod.POST, new HttpEntity<>(body, signed), Void.class)
			.getStatusCode()).isEqualTo(HttpStatus.OK);

		// download da mídia usou o Bearer da Cloud API
		assertThat(BSP_REQUESTS).anyMatch(r -> r.path().equals("/media-img-1") && "Bearer token-meta".equals(r.apiKey()));

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			TenantContext.setTenantId(tenantId);
			try {
				assertThat(conversationRepository.count()).isEqualTo(1);
			}
			finally {
				TenantContext.clear();
			}
		});

		// agente vê a imagem com URL pré-assinada que serve os bytes originais
		TenantContext.setTenantId(tenantId);
		UUID conversationId = conversationRepository.findAll().getFirst().getId();
		TenantContext.clear();
		HttpHeaders bearer = new HttpHeaders();
		bearer.setBearerAuth(adminToken);
		ResponseEntity<Map> messages = rest.exchange("/api/conversations/" + conversationId + "/messages",
				HttpMethod.GET, new HttpEntity<>(bearer), Map.class);
		Map<String, Object> received = ((List<Map<String, Object>>) messages.getBody().get("content")).getFirst();
		assertThat(received).containsEntry("kind", "IMAGE").containsEntry("text", "Serve nesse espaço?");
		String presigned = (String) received.get("mediaUrl");
		assertThat(presigned).startsWith("http").doesNotContain("storage://");
		assertThat(new org.springframework.web.client.RestTemplate().getForObject(java.net.URI.create(presigned),
				byte[].class)).isEqualTo(PNG_BYTES);

		// agente responde com mídia: payload Cloud API leva link pré-assinado + caption
		TenantContext.setTenantId(tenantId);
		String storageKey = storageService.store(tenantId, "planta.png", "image/png", PNG_BYTES);
		inboxService.replyAsAgent(conversationId, agentUserId,
				com.omnichannel.channels.MessageContent.media(com.omnichannel.channels.MessageKind.IMAGE,
						storageKey, "image/png", "Cabe sim — veja a planta"));
		TenantContext.clear();

		CapturedRequest sent = BSP_REQUESTS.stream()
			.filter(r -> r.path().equals("/555000111/messages"))
			.findFirst()
			.orElseThrow();
		assertThat(sent.body()).contains("\"type\":\"image\"").contains("\"link\":\"http")
			.contains("Cabe sim — veja a planta")
			.doesNotContain("storage://");
	}

	@Test
	void rotacaoDeTokenPreservaWebhookEUsaTokenNovoNoEnvio() {
		CloudChannel channel = connectCloudChannel();

		// troca SÓ o access_token (token da Meta expira em ~24h)
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(adminToken);
		ResponseEntity<Map> updated = rest.exchange("/api/channels/" + channel.channelId() + "/credentials",
				HttpMethod.PUT, new HttpEntity<>(Map.of("access_token", "token-meta-novo"), headers), Map.class);
		assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

		// webhook_token/verify_token preservados: verificação da Meta continua passando
		ResponseEntity<String> challenge = rest.getForEntity(channel.webhookPath()
				+ "?hub.mode=subscribe&hub.verify_token=" + channel.verifyToken() + "&hub.challenge=pos-rotacao",
				String.class);
		assertThat(challenge.getBody()).isEqualTo("pos-rotacao");

		// mensagem entra (assinada com o app_secret preservado) e a resposta sai com o token NOVO
		String body = cloudApiPayload("5551955550000", "wamid.rot-1", "Ainda funciona?");
		HttpHeaders signed = new HttpHeaders();
		signed.setContentType(MediaType.APPLICATION_JSON);
		signed.set("X-Hub-Signature-256", "sha256=" + MetaSignatures.hmacSha256("segredo-meta", body));
		assertThat(rest.exchange(channel.webhookPath(), HttpMethod.POST, new HttpEntity<>(body, signed), Void.class)
			.getStatusCode()).isEqualTo(HttpStatus.OK);
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			TenantContext.setTenantId(tenantId);
			try {
				assertThat(conversationRepository.count()).isEqualTo(1);
			}
			finally {
				TenantContext.clear();
			}
		});

		TenantContext.setTenantId(tenantId);
		UUID conversationId = conversationRepository.findAll().getFirst().getId();
		inboxService.replyAsAgent(conversationId, agentUserId,
				com.omnichannel.channels.MessageContent.text("Funciona com o token novo!"));
		TenantContext.clear();

		CapturedRequest sent = BSP_REQUESTS.stream()
			.filter(r -> r.path().equals("/555000111/messages"))
			.findFirst()
			.orElseThrow();
		assertThat(sent.apiKey()).isEqualTo("Bearer token-meta-novo");
	}

	@Test
	void webhookComTokenErradoRecebe403ENadaEntra() {
		HttpHeaders json = new HttpHeaders();
		json.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<Void> negado = rest.exchange(
				"/api/whatsapp/webhook/" + channelId + "/token-forjado", HttpMethod.POST,
				new HttpEntity<>(cloudApiPayload("5551888880000", "wamid.forjado", "invasão"), json), Void.class);
		assertThat(negado.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

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
	void eventoDeStatusSemMensagensERespondido200SemEfeito() {
		HttpHeaders json = new HttpHeaders();
		json.setContentType(MediaType.APPLICATION_JSON);
		String statusPayload = """
				{"object":"whatsapp_business_account","entry":[{"id":"123","changes":[{"field":"messages",
				"value":{"messaging_product":"whatsapp","statuses":[{"id":"wamid.x","status":"delivered"}]}}]}]}""";
		ResponseEntity<Void> ok = rest.exchange(webhookPath, HttpMethod.POST,
				new HttpEntity<>(statusPayload, json), Void.class);
		assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
	}
}
