package com.omnichannel.inbox;

import java.net.URI;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Mídia de ponta a ponta: agente sobe um arquivo (multipart), anexa na
 * resposta, e todo mundo (inbox e visitante do webchat) recebe uma URL
 * pré-assinada que de fato serve os bytes do MinIO. A referência interna
 * ({@code storage://}) nunca vaza nas respostas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class MediaIntegrationTest {

	static final byte[] PNG_MINIMO = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4 };

	@Autowired
	TestRestTemplate rest;

	@Autowired
	AuthService authService;

	@Autowired
	ChannelService channelService;

	@Autowired
	com.omnichannel.ai.AiSettingsService aiSettingsService;

	@Autowired
	org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

	String agentToken;
	UUID tenantId;
	Channel channel;
	String visitorId;

	@BeforeEach
	void preparaTenantECanal() {
		var tokens = authService.signup("Loja Mídia", "Atendente", "media-" + UUID.randomUUID() + "@x.com",
				"senha-forte-1");
		agentToken = tokens.accessToken();
		tenantId = UUID.fromString(jwtDecoder.decode(agentToken).getClaimAsString("tenant_id"));
		TenantContext.setTenantId(tenantId);
		channel = channelService.connect(tenantId, ChannelType.WEBCHAT, "Chat do Site", Map.of());
		aiSettingsService.update(tenantId, false, new java.math.BigDecimal("0.7"), null);
		TenantContext.clear();
		visitorId = "vis-" + UUID.randomUUID();
	}

	@Test
	void agenteEnviaImagemEVisitanteRecebeUrlPreAssinadaQueServeOsBytes() {
		// visitante abre a conversa
		rest.postForEntity("/api/webchat/" + channel.getId() + "/messages",
				Map.of("visitorId", visitorId, "text", "Tem foto do produto?"), Void.class);
		await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(500)).untilAsserted(
				() -> assertThat(agentGet("/api/conversations").getBody().get("totalElements")).isEqualTo(1));
		String conversationId = (String) ((List<Map>) agentGet("/api/conversations").getBody().get("content"))
			.getFirst()
			.get("id");

		// upload multipart
		ResponseEntity<Map> uploaded = upload("produto.png", MediaType.IMAGE_PNG_VALUE, PNG_MINIMO);
		assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String mediaUrl = (String) uploaded.getBody().get("mediaUrl");
		assertThat(mediaUrl).startsWith("storage://");
		assertThat(uploaded.getBody().get("kind")).isEqualTo("IMAGE");
		assertThat((String) uploaded.getBody().get("previewUrl")).startsWith("http");

		// resposta com a mídia anexada
		ResponseEntity<Map> reply = agentPost("/api/conversations/" + conversationId + "/reply",
				Map.of("kind", "IMAGE", "text", "Segue a foto!", "mediaUrl", mediaUrl, "mimeType", "image/png"));
		assertThat(reply.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String replyMediaUrl = (String) reply.getBody().get("mediaUrl");
		assertThat(replyMediaUrl).startsWith("http").doesNotContain("storage://");

		// a URL pré-assinada realmente serve os bytes originais
		byte[] served = new RestTemplate().getForObject(URI.create(replyMediaUrl), byte[].class);
		assertThat(served).isEqualTo(PNG_MINIMO);

		// o visitante também recebe URL utilizável, nunca a referência interna
		ResponseEntity<List> thread = rest
			.getForEntity("/api/webchat/" + channel.getId() + "/messages?visitorId=" + visitorId, List.class);
		List<Map<String, Object>> messages = thread.getBody();
		assertThat(messages).hasSize(2);
		Map<String, Object> imagem = messages.get(1);
		assertThat(imagem).containsEntry("kind", "IMAGE").containsEntry("text", "Segue a foto!");
		assertThat((String) imagem.get("mediaUrl")).startsWith("http").doesNotContain("storage://");
	}

	@Test
	void uploadRejeitaTipoNaoSuportadoESemToken() {
		ResponseEntity<Map> executavel = upload("virus.exe", "application/x-msdownload", new byte[] { 1, 2 });
		assertThat(executavel.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

		MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
		form.add("file", fileResource("a.png", MediaType.IMAGE_PNG_VALUE, PNG_MINIMO));
		RequestEntity<MultiValueMap<String, Object>> semToken = RequestEntity.post(URI.create("/api/uploads"))
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.body(form);
		assertThat(rest.exchange(semToken, Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	private ResponseEntity<Map> upload(String filename, String contentType, byte[] bytes) {
		MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
		form.add("file", fileResource(filename, contentType, bytes));
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(agentToken);
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		return rest.exchange("/api/uploads", HttpMethod.POST, new HttpEntity<>(form, headers), Map.class);
	}

	private HttpEntity<ByteArrayResource> fileResource(String filename, String contentType, byte[] bytes) {
		HttpHeaders partHeaders = new HttpHeaders();
		partHeaders.setContentType(MediaType.parseMediaType(contentType));
		partHeaders.setContentDispositionFormData("file", filename);
		return new HttpEntity<>(new ByteArrayResource(bytes) {
			@Override
			public String getFilename() {
				return filename;
			}
		}, partHeaders);
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
