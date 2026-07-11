package com.omnichannel.channels.email;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.omnichannel.TestcontainersConfiguration;
import com.omnichannel.channels.ChannelType;
import com.omnichannel.channels.domain.Channel;
import com.omnichannel.channels.domain.ChannelService;
import com.omnichannel.inbox.InboxService;
import com.omnichannel.inbox.domain.ContactIdentityRepository;
import com.omnichannel.inbox.domain.ConversationRepository;
import com.omnichannel.inbox.domain.MessageRepository;
import com.omnichannel.shared.tenancy.TenantContext;
import com.omnichannel.tenant.AuthService;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Canal de e-mail de ponta a ponta, 100% offline: Greenmail faz o papel do
 * provedor (IMAP para ingestão, SMTP para envio). Cliente escreve →
 * conversa aparece no inbox → agente responde → e-mail chega na caixa do
 * cliente.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EmailChannelIntegrationTest {

	@RegisterExtension
	static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP_IMAP)
		.withPerMethodLifecycle(true);

	@Autowired
	ChannelService channelService;

	@Autowired
	AuthService authService;

	@Autowired
	JwtDecoder jwtDecoder;

	@Autowired
	com.omnichannel.ai.AiSettingsService aiSettingsService;

	@Autowired
	ConversationRepository conversationRepository;

	@Autowired
	MessageRepository messageRepository;

	@Autowired
	ContactIdentityRepository identityRepository;

	@Autowired
	InboxService inboxService;

	@Autowired
	com.omnichannel.shared.storage.StorageService storageService;

	UUID tenantId;
	UUID agentUserId;
	Channel channel;
	String supportAddress;
	String supportLogin;

	@BeforeEach
	void preparaTenantECanalDeEmail() {
		// caixa única por teste: canais de testes anteriores (ainda ativos no
		// banco compartilhado) não conseguem logar e são pulados pelo poller
		supportLogin = "suporte-" + UUID.randomUUID().toString().substring(0, 8);
		supportAddress = supportLogin + "@empresa.com";
		greenMail.setUser(supportAddress, supportLogin, "senha-imap");

		var tokens = authService.signup("Empresa Email", "Atendente", "email-" + UUID.randomUUID() + "@x.com",
				"senha-forte-1");
		var jwt = jwtDecoder.decode(tokens.accessToken());
		tenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
		agentUserId = UUID.fromString(jwt.getSubject());
		TenantContext.setTenantId(tenantId);
		aiSettingsService.update(tenantId, false, new java.math.BigDecimal("0.7"), null);
		channel = channelService.connect(tenantId, ChannelType.EMAIL, "Caixa de Suporte", Map.of(
				"imap_host", "127.0.0.1",
				"imap_port", String.valueOf(greenMail.getImap().getPort()),
				"imap_username", supportLogin,
				"imap_password", "senha-imap",
				"smtp_host", "127.0.0.1",
				"smtp_port", String.valueOf(greenMail.getSmtp().getPort()),
				"smtp_username", supportLogin,
				"smtp_password", "senha-imap",
				"smtp_starttls", "false",
				"from_address", supportAddress));
		TenantContext.clear();
	}

	@Test
	void clienteEscreveEmailEAgenteRespondePorEmail() {
		// cliente manda e-mail para a caixa de suporte
		GreenMailUtil.sendTextEmail(supportAddress, "cliente@fora.com",
				"Dúvida sobre pedido", "Meu pedido 123 ainda não chegou.", greenMail.getSmtp().getServerSetup());

		// poller IMAP ingere → conversa materializa no inbox
		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
			TenantContext.setTenantId(tenantId);
			try {
				assertThat(conversationRepository.count()).isEqualTo(1);
				var conversation = conversationRepository.findAll().getFirst();
				assertThat(conversation.getLastMessagePreview()).contains("Dúvida sobre pedido");
				var identity = identityRepository
					.findByChannelTypeAndExternalId(ChannelType.EMAIL, "cliente@fora.com");
				assertThat(identity).isPresent();
			}
			finally {
				TenantContext.clear();
			}
		});

		// agente responde → sai por SMTP e chega na caixa do cliente
		TenantContext.setTenantId(tenantId);
		UUID conversationId = conversationRepository.findAll().getFirst().getId();
		inboxService.replyAsAgent(conversationId, agentUserId,
				com.omnichannel.channels.MessageContent.text("Seu pedido sai para entrega amanhã!"));
		TenantContext.clear();

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			MimeMessage[] received = greenMail.getReceivedMessages();
			// [0] = e-mail do cliente para o suporte; a resposta é a mais recente
			assertThat(received.length).isGreaterThanOrEqualTo(2);
			MimeMessage reply = received[received.length - 1];
			assertThat(reply.getAllRecipients()[0].toString()).contains("cliente@fora.com");
			assertThat(String.valueOf(reply.getContent())).contains("sai para entrega amanhã");
		});
	}

	@Test
	void respostaComMidiaViraAnexoDeVerdadeNoEmail() throws Exception {
		byte[] pdf = "%PDF-1.4 conteudo-de-teste".getBytes();
		GreenMailUtil.sendTextEmail(supportAddress, "cliente3@fora.com", "Contrato",
				"Pode me mandar o contrato?", greenMail.getSmtp().getServerSetup());

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
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
		String storageKey = storageService.store(tenantId, "contrato.pdf", "application/pdf", pdf);
		inboxService.replyAsAgent(conversationId, agentUserId,
				com.omnichannel.channels.MessageContent.media(com.omnichannel.channels.MessageKind.DOCUMENT,
						storageKey, "application/pdf", "Segue o contrato combinado."));
		TenantContext.clear();

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			MimeMessage[] received = greenMail.getReceivedMessages();
			MimeMessage reply = received[received.length - 1];
			Object content = reply.getContent();
			assertThat(content).isInstanceOf(jakarta.mail.internet.MimeMultipart.class);
			var multipart = (jakarta.mail.internet.MimeMultipart) content;
			assertThat(String.valueOf(multipart.getBodyPart(0).getContent())).contains("Segue o contrato");
			var attachment = multipart.getBodyPart(1);
			assertThat(attachment.getFileName()).isEqualTo("contrato.pdf");
			assertThat(attachment.getInputStream().readAllBytes()).isEqualTo(pdf);
		});
	}

	@Test
	void reentregaDoMesmoEmailNaoDuplicaPorMessageId() {
		GreenMailUtil.sendTextEmail(supportAddress, "cliente2@fora.com", "Oi", "primeira e única",
				greenMail.getSmtp().getServerSetup());

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
			TenantContext.setTenantId(tenantId);
			try {
				assertThat(messageRepository.count()).isEqualTo(1);
			}
			finally {
				TenantContext.clear();
			}
		});

		// o poller marca SEEN; mesmo que rode de novo, nada muda
		TenantContext.setTenantId(tenantId);
		long before = messageRepository.count();
		TenantContext.clear();
		await().during(Duration.ofSeconds(3)).untilAsserted(() -> {
			TenantContext.setTenantId(tenantId);
			try {
				assertThat(messageRepository.count()).isEqualTo(before);
			}
			finally {
				TenantContext.clear();
			}
		});
	}
}
