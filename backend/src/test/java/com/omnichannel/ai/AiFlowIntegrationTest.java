package com.omnichannel.ai;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.omnichannel.TestcontainersConfiguration;
import com.omnichannel.TestcontainersConfiguration.FakeAnswerGenerator;
import com.omnichannel.ai.domain.ResolutionLedgerEntry;
import com.omnichannel.ai.domain.ResolutionLedgerRepository;
import com.omnichannel.channels.ChannelType;
import com.omnichannel.channels.InboundMessageGateway;
import com.omnichannel.channels.InboundMessageReceived;
import com.omnichannel.channels.MessageContent;
import com.omnichannel.channels.domain.Channel;
import com.omnichannel.channels.domain.ChannelService;
import com.omnichannel.inbox.InboxService;
import com.omnichannel.inbox.domain.ConversationRepository;
import com.omnichannel.inbox.domain.ConversationStatus;
import com.omnichannel.inbox.domain.MessageRepository;
import com.omnichannel.inbox.domain.MessageSender;
import com.omnichannel.shared.tenancy.TenantContext;
import com.omnichannel.tenant.AuthService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Fluxo do AI Worker de ponta a ponta com LLM fake: RAG alimenta o
 * gerador, o portão de confiança decide resposta vs. handoff, e TODA
 * decisão gera registro no livro-razão — requisito funcional
 * (docs/01-visao-e-produto.md).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AiFlowIntegrationTest {

	@Autowired
	InboundMessageGateway gateway;

	@Autowired
	ChannelService channelService;

	@Autowired
	KnowledgeService knowledgeService;

	@Autowired
	AiSettingsService aiSettingsService;

	@Autowired
	AuthService authService;

	@Autowired
	JwtDecoder jwtDecoder;

	@Autowired
	ConversationRepository conversationRepository;

	@Autowired
	MessageRepository messageRepository;

	@Autowired
	ResolutionLedgerRepository ledgerRepository;

	@Autowired
	InboxService inboxService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	UUID tenantId;
	UUID adminUserId;
	Channel channel;

	@BeforeEach
	void preparaTenantCanalEConhecimento() {
		FakeAnswerGenerator.reset();
		var tokens = authService.signup("Loja IA", "Admin", "ia-" + UUID.randomUUID() + "@x.com", "senha-forte-1");
		var jwt = jwtDecoder.decode(tokens.accessToken());
		tenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
		adminUserId = UUID.fromString(jwt.getSubject());
		TenantContext.setTenantId(tenantId);
		channel = channelService.connect(tenantId, ChannelType.WEBCHAT, "Chat", Map.of());
		knowledgeService.addDocument(tenantId, "Política de Entrega",
				"Entregamos em todo o Brasil.\n\nO prazo de entrega é de 3 dias úteis para capitais.");
		TenantContext.clear();
	}

	@AfterEach
	void limpaContexto() {
		TenantContext.clear();
	}

	@Test
	void confiancaAltaRespondeSozinhaERegistraNoLedger() {
		FakeAnswerGenerator.nextConfidence = 0.95;
		FakeAnswerGenerator.nextAnswer = "Entregamos em 3 dias úteis para capitais!";

		gateway.receive(inbound("cliente-1", "m-alta", "Qual o prazo de entrega?"));

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			TenantContext.setTenantId(tenantId);
			try {
				List<ResolutionLedgerEntry> ledger = ledgerRepository.findAll();
				assertThat(ledger).hasSize(1);
				ResolutionLedgerEntry entry = ledger.getFirst();
				assertThat(entry.getAction()).isEqualTo(ResolutionLedgerEntry.Action.ANSWERED);
				assertThat(entry.getConfidence()).isEqualByComparingTo(new BigDecimal("0.950"));
				assertThat(entry.getAiMessageId()).isNotNull();

				// a resposta da IA existe na conversa, com confiança gravada
				var aiMessage = messageRepository.findById(entry.getAiMessageId()).orElseThrow();
				assertThat(aiMessage.getSender()).isEqualTo(MessageSender.AI);
				assertThat(aiMessage.getText()).contains("3 dias úteis");

				// conversa marcada como conduzida pela IA
				var conversation = conversationRepository.findById(entry.getConversationId()).orElseThrow();
				assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.WITH_AI);
			}
			finally {
				TenantContext.clear();
			}
		});

		// o RAG alimentou o gerador com o chunk relevante da base
		assertThat(FakeAnswerGenerator.received).hasSize(1);
		assertThat(FakeAnswerGenerator.received.getFirst().knowledgeChunks())
			.anySatisfy(chunk -> assertThat(chunk).contains("3 dias úteis"));
	}

	@Test
	void confiancaBaixaFazHandoffComResumoESemResposta() {
		FakeAnswerGenerator.nextConfidence = 0.30;

		gateway.receive(inbound("cliente-2", "m-baixa", "Quero renegociar o contrato da minha empresa"));

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			TenantContext.setTenantId(tenantId);
			try {
				List<ResolutionLedgerEntry> ledger = ledgerRepository.findAll();
				assertThat(ledger).hasSize(1);
				ResolutionLedgerEntry entry = ledger.getFirst();
				assertThat(entry.getAction()).isEqualTo(ResolutionLedgerEntry.Action.HANDOFF);
				assertThat(entry.getHandoffSummary()).contains("resumo-de-teste");
				assertThat(entry.getAiMessageId()).isNull();

				// nenhuma mensagem de IA; conversa na fila humana
				var conversation = conversationRepository.findById(entry.getConversationId()).orElseThrow();
				assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.OPEN);
				assertThat(messageRepository.findTop10ByConversationIdOrderByCreatedAtDesc(entry.getConversationId()))
					.noneMatch(message -> message.getSender() == MessageSender.AI);
			}
			finally {
				TenantContext.clear();
			}
		});
	}

	@Test
	void pedidoExplicitoDeHumanoTransfereDeVerdadeMesmoComConfiancaAlta() {
		// a IA está confiante E o cliente pediu uma pessoa: a promessa de
		// encaminhar tem que virar handoff real, não só uma frase educada
		FakeAnswerGenerator.nextConfidence = 0.95;
		FakeAnswerGenerator.nextAnswer = "Claro! Vou te passar para alguém da equipe agora mesmo.";
		FakeAnswerGenerator.nextHandoffRequested = true;

		gateway.receive(inbound("cliente-humano", "m-humano", "quero falar com um responsável"));

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			TenantContext.setTenantId(tenantId);
			try {
				List<ResolutionLedgerEntry> ledger = ledgerRepository.findAll();
				assertThat(ledger).hasSize(1);
				ResolutionLedgerEntry entry = ledger.getFirst();
				assertThat(entry.getAction()).isEqualTo(ResolutionLedgerEntry.Action.HANDOFF);
				assertThat(entry.getHandoffSummary()).contains("Cliente pediu atendimento humano");

				// o cliente recebeu a mensagem de cortesia E a conversa foi
				// para a fila humana — a promessa foi cumprida
				var conversation = conversationRepository.findById(entry.getConversationId()).orElseThrow();
				assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.OPEN);
				assertThat(messageRepository.findTop10ByConversationIdOrderByCreatedAtDesc(entry.getConversationId()))
					.anyMatch(message -> message.getSender() == MessageSender.AI
							&& message.getText().contains("passar para alguém"));
			}
			finally {
				TenantContext.clear();
			}
		});
	}

	@Test
	void intencaoDeCompraDetectadaEtiquetaAConversaParaOComercial() {
		FakeAnswerGenerator.nextConfidence = 0.95;
		FakeAnswerGenerator.nextAnswer = "O plano anual sai por R$ 990 — posso te mandar a proposta?";
		FakeAnswerGenerator.nextBuyingIntent = true;

		gateway.receive(inbound("cliente-comprador", "m-compra", "Quero fechar o plano anual, qual o preço?"));

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			TenantContext.setTenantId(tenantId);
			try {
				List<ResolutionLedgerEntry> ledger = ledgerRepository.findAll();
				assertThat(ledger).hasSize(1);
				var conversation = conversationRepository.findById(ledger.getFirst().getConversationId())
					.orElseThrow();
				assertThat(conversation.getTags()).contains("possível venda");
				// a IA continua respondendo normalmente — a etiqueta é só o sinal
				assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.WITH_AI);
			}
			finally {
				TenantContext.clear();
			}
		});
	}

	@Test
	void conversaComHumanoNaoRecebeInterferenciaDaIa() {
		FakeAnswerGenerator.nextConfidence = 0.99;

		gateway.receive(inbound("cliente-3", "m-humano-1", "oi"));
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			TenantContext.setTenantId(tenantId);
			try {
				assertThat(ledgerRepository.count()).isEqualTo(1);
			}
			finally {
				TenantContext.clear();
			}
		});

		// agente assume a conversa
		TenantContext.setTenantId(tenantId);
		UUID conversationId = conversationRepository.findAll().getFirst().getId();
		inboxService.assign(conversationId, adminUserId);
		TenantContext.clear();

		gateway.receive(inbound("cliente-3", "m-humano-2", "e o prazo?"));

		// a segunda mensagem NÃO gera decisão de IA (conversa WITH_HUMAN)
		await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			TenantContext.setTenantId(tenantId);
			try {
				assertThat(ledgerRepository.count()).isEqualTo(1);
			}
			finally {
				TenantContext.clear();
			}
		});
	}

	@Test
	void iaDesabilitadaNasConfiguracoesFicaEmSilencio() {
		TenantContext.setTenantId(tenantId);
		aiSettingsService.update(tenantId, false, new BigDecimal("0.700"), null);
		TenantContext.clear();

		gateway.receive(inbound("cliente-4", "m-desligada", "Qual o prazo?"));

		await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			TenantContext.setTenantId(tenantId);
			try {
				assertThat(ledgerRepository.count()).isZero();
			}
			finally {
				TenantContext.clear();
			}
		});
	}

	@Test
	void vetoresDaBaseFicamIsoladosPorTenantNoBanco() {
		// tenant A tem 1+ chunks; sob o papel de runtime sem contexto, zero
		Long semContexto = jdbcTemplate.execute((java.sql.Connection connection) -> {
			try (var statement = connection.createStatement()) {
				statement.execute("SET ROLE app_runtime");
				try (var resultSet = statement.executeQuery("SELECT count(*) FROM vector_store")) {
					resultSet.next();
					return resultSet.getLong(1);
				}
			}
			finally {
				try (var reset = connection.createStatement()) {
					reset.execute("RESET ROLE");
				}
			}
		});
		assertThat(semContexto).isZero();

		TenantContext.setTenantId(tenantId);
		try {
			Long doTenant = jdbcTemplate.queryForObject("SELECT count(*) FROM vector_store", Long.class);
			assertThat(doTenant).isGreaterThan(0);
		}
		finally {
			TenantContext.clear();
		}
	}

	private InboundMessageReceived inbound(String externalId, String messageId, String text) {
		return new InboundMessageReceived(tenantId, channel.getId(), ChannelType.WEBCHAT, externalId, "Cliente",
				messageId + "-" + UUID.randomUUID(), MessageContent.text(text), Instant.now());
	}
}
