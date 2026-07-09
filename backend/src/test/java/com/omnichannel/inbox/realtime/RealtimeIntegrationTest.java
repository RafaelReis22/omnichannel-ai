package com.omnichannel.inbox.realtime;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Push em tempo real: dashboard conectado recebe atividade da conversa. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RealtimeIntegrationTest {

	@LocalServerPort
	int port;

	@Autowired
	AuthService authService;

	@Autowired
	ChannelService channelService;

	@Autowired
	InboundMessageGateway gateway;

	@Autowired
	JwtDecoder jwtDecoder;

	@Autowired
	com.omnichannel.ai.AiSettingsService aiSettingsService;

	String token;
	UUID tenantId;
	Channel channel;

	@BeforeEach
	void preparaTenantECanal() {
		var tokens = authService.signup("Empresa RT", "Agente", "rt-" + UUID.randomUUID() + "@x.com",
				"senha-forte-1");
		token = tokens.accessToken();
		tenantId = UUID.fromString(jwtDecoder.decode(token).getClaimAsString("tenant_id"));
		TenantContext.setTenantId(tenantId);
		aiSettingsService.update(tenantId, false, new java.math.BigDecimal("0.7"), null);
		channel = channelService.connect(tenantId, ChannelType.WEBCHAT, "Chat", Map.of());
		TenantContext.clear();
	}

	@Test
	void dashboardConectadoRecebePushQuandoMensagemChega() throws Exception {
		BlockingQueue<String> frames = new LinkedBlockingQueue<>();
		WebSocketSession session = connect(token, frames);
		try {
			gateway.receive(new InboundMessageReceived(tenantId, channel.getId(), ChannelType.WEBCHAT,
					"vis-rt-1", "Visitante", "rt-m-" + UUID.randomUUID(),
					MessageContent.text("olá em tempo real"), Instant.now()));

			String frame = frames.poll(15, TimeUnit.SECONDS);
			assertThat(frame).isNotNull().contains("\"type\":\"conversation\"").contains("conversationId");
		}
		finally {
			session.close();
		}
	}

	@Test
	void handshakeSemTokenOuComTokenInvalidoERecusado() {
		assertThatThrownBy(() -> connect(null, new LinkedBlockingQueue<>()))
			.isInstanceOf(ExecutionException.class);
		assertThatThrownBy(() -> connect("token-invalido", new LinkedBlockingQueue<>()))
			.isInstanceOf(ExecutionException.class);
	}

	private WebSocketSession connect(String accessToken, BlockingQueue<String> frames) throws Exception {
		String url = "ws://localhost:" + port + "/ws" + (accessToken == null ? "" : "?token=" + accessToken);
		return new StandardWebSocketClient()
			.execute(new TextWebSocketHandler() {
				@Override
				protected void handleTextMessage(WebSocketSession session, TextMessage message) {
					frames.add(message.getPayload());
				}
			}, URI.create(url).toString())
			.get(Duration.ofSeconds(10).toSeconds(), TimeUnit.SECONDS);
	}
}
