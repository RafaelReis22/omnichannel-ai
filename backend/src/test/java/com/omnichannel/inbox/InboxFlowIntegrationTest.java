package com.omnichannel.inbox;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.omnichannel.TestcontainersConfiguration;
import com.omnichannel.channels.ChannelType;
import com.omnichannel.channels.InboundMessageGateway;
import com.omnichannel.channels.InboundMessageReceived;
import com.omnichannel.channels.MessageContent;
import com.omnichannel.channels.domain.Channel;
import com.omnichannel.channels.domain.ChannelService;
import com.omnichannel.inbox.domain.ContactRepository;
import com.omnichannel.inbox.domain.ConversationRepository;
import com.omnichannel.inbox.domain.ConversationStatus;
import com.omnichannel.inbox.domain.MessageRepository;
import com.omnichannel.shared.tenancy.TenantContext;
import com.omnichannel.tenant.AuthService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Caminho crítico do produto (docs/03-arquitetura.md): mensagem entra pelo
 * gateway → evento pelo outbox → listener assíncrono materializa
 * contato/conversa/mensagem, com deduplicação e reabertura.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InboxFlowIntegrationTest {

	@Autowired
	InboundMessageGateway gateway;

	@Autowired
	ChannelService channelService;

	@Autowired
	AuthService authService;

	@Autowired
	com.omnichannel.ai.AiSettingsService aiSettingsService;

	@Autowired
	JwtDecoder jwtDecoder;

	@Autowired
	ContactRepository contactRepository;

	@Autowired
	ConversationRepository conversationRepository;

	@Autowired
	MessageRepository messageRepository;

	@Autowired
	InboxService inboxService;

	UUID tenantId;
	Channel channel;

	@BeforeEach
	void criaTenantECanal() {
		var tokens = authService.signup("Empresa Inbox", "Admin", "inbox-" + UUID.randomUUID() + "@x.com",
				"senha-forte-1");
		tenantId = UUID.fromString(jwtDecoder.decode(tokens.accessToken()).getClaimAsString("tenant_id"));
		TenantContext.setTenantId(tenantId);
		channel = channelService.connect(tenantId, ChannelType.WEBCHAT, "Chat do Site", Map.of("k", "v"));
		aiSettingsService.update(tenantId, false, new java.math.BigDecimal("0.7"), null); // IA fora destes testes
		TenantContext.clear();
	}

	@AfterEach
	void limpaContexto() {
		TenantContext.clear();
	}

	@Test
	void mensagemNovaCriaContatoConversaEMensagem() {
		gateway.receive(inbound("visitante-1", "Maria", "m-1", "Oi, qual o preço?"));

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			TenantContext.setTenantId(tenantId);
			try {
			assertThat(conversationRepository.count()).isEqualTo(1);
			var conversation = conversationRepository.findAll().getFirst();
			assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.OPEN);
			assertThat(conversation.getLastMessagePreview()).isEqualTo("Oi, qual o preço?");
			assertThat(contactRepository.findById(conversation.getContactId()).orElseThrow().getName())
				.isEqualTo("Maria");
			}
			finally {
				TenantContext.clear();
			}
		});
	}

	@Test
	void segundaMensagemDoMesmoContatoReusaConversaESegundaIdentidadeNaoDuplicaContato() {
		gateway.receive(inbound("visitante-2", "João", "m-2", "primeira"));
		gateway.receive(inbound("visitante-2", "João", "m-3", "segunda"));

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			TenantContext.setTenantId(tenantId);
			try {
			assertThat(contactRepository.count()).isEqualTo(1);
			assertThat(conversationRepository.count()).isEqualTo(1);
			assertThat(messageRepository.count()).isEqualTo(2);
			}
			finally {
				TenantContext.clear();
			}
		});
	}

	@Test
	void reentregaDeWebhookComMesmoIdNaoDuplicaMensagem() {
		gateway.receive(inbound("visitante-3", "Ana", "m-repetida", "oi"));
		gateway.receive(inbound("visitante-3", "Ana", "m-repetida", "oi"));

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			TenantContext.setTenantId(tenantId);
			try {
			assertThat(messageRepository.count()).isEqualTo(1);
			}
			finally {
				TenantContext.clear();
			}
		});
	}

	@Test
	void mensagemEmConversaResolvidaReabre() {
		gateway.receive(inbound("visitante-4", "Bia", "m-4", "oi"));

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
		inboxService.resolve(conversationId);
		TenantContext.clear();

		gateway.receive(inbound("visitante-4", "Bia", "m-5", "voltei"));

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			TenantContext.setTenantId(tenantId);
			try {
				var conversation = conversationRepository.findById(conversationId).orElseThrow();
				assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.OPEN);
				assertThat(conversationRepository.count()).isEqualTo(1); // reusa, não cria outra
			}
			finally {
				TenantContext.clear();
			}
		});
	}

	private InboundMessageReceived inbound(String externalId, String name, String messageId, String text) {
		return new InboundMessageReceived(tenantId, channel.getId(), ChannelType.WEBCHAT, externalId, name,
				messageId, MessageContent.text(text), Instant.now());
	}
}
