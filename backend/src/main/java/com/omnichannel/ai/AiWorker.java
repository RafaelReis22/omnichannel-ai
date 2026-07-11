package com.omnichannel.ai;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.omnichannel.ai.domain.AiSettings;
import com.omnichannel.ai.domain.AiSettingsRepository;
import com.omnichannel.ai.domain.ResolutionLedgerEntry;
import com.omnichannel.ai.domain.ResolutionLedgerRepository;
import com.omnichannel.channels.MessageContent;
import com.omnichannel.inbox.ContactMessageRegistered;
import com.omnichannel.inbox.InboxService;
import com.omnichannel.inbox.domain.Conversation;
import com.omnichannel.inbox.domain.Message;
import com.omnichannel.shared.tenancy.TenantContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AI Worker (docs/03-arquitetura.md): consome mensagens de contato
 * materializadas, decide com RAG + histórico e aplica o portão de
 * confiança — acima do limiar responde sozinho; abaixo, devolve à fila
 * humana com resumo. TODA decisão vira registro no livro-razão.
 */
@Component
class AiWorker {

	private static final Logger log = LoggerFactory.getLogger(AiWorker.class);

	private final InboxService inboxService;
	private final KnowledgeService knowledgeService;
	private final AnswerGenerator answerGenerator;
	private final AiSettingsRepository aiSettingsRepository;
	private final ResolutionLedgerRepository ledgerRepository;
	private final TransactionTemplate tenantScopedTransaction;

	AiWorker(InboxService inboxService, KnowledgeService knowledgeService, AnswerGenerator answerGenerator,
			AiSettingsRepository aiSettingsRepository, ResolutionLedgerRepository ledgerRepository,
			PlatformTransactionManager transactionManager) {
		this.inboxService = inboxService;
		this.knowledgeService = knowledgeService;
		this.answerGenerator = answerGenerator;
		this.aiSettingsRepository = aiSettingsRepository;
		this.ledgerRepository = ledgerRepository;
		this.tenantScopedTransaction = new TransactionTemplate(transactionManager);
		this.tenantScopedTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@ApplicationModuleListener
	void on(ContactMessageRegistered event) {
		TenantContext.setTenantId(event.tenantId());
		try {
			// 1ª transação: decide se a IA deve agir e coleta o contexto.
			// A chamada ao LLM fica FORA de transação (latência de segundos
			// não pode segurar conexão de banco).
			DecisionContext context = tenantScopedTransaction.execute(status -> collectContext(event));
			if (context == null) {
				return;
			}

			AnswerGenerator.GeneratedAnswer answer;
			String handoffSummary;
			boolean confident;
			try {
				answer = answerGenerator.generate(new AnswerGenerator.AnswerRequest(context.tone(),
						context.assistantName(), context.knowledgeChunks(), context.history(), event.text()));
				// pedido explícito de humano vence o portão de confiança
				confident = !answer.handoffRequested()
						&& BigDecimal.valueOf(answer.confidence()).compareTo(context.threshold()) >= 0;
				// resumo do handoff também é LLM: fora de transação
				handoffSummary = confident ? null
						: (answer.handoffRequested() ? "Cliente pediu atendimento humano. " : "")
								+ answerGenerator.summarize(context.history());
			}
			catch (Exception llmFailure) {
				// FAIL-OPEN: LLM indisponível não pode deixar o cliente no vácuo.
				// A conversa vai para a fila humana com registro explícito.
				log.error("LLM falhou para conversa {}: {}", event.conversationId(), llmFailure.getMessage());
				answer = new AnswerGenerator.GeneratedAnswer(null, 0.0);
				confident = false;
				handoffSummary = "IA indisponível (erro técnico) — conversa encaminhada para atendimento humano.";
			}
			final var decidedAnswer = answer;
			final var decidedConfident = confident;
			final var decidedSummary = handoffSummary;

			// 2ª transação: aplica a decisão e grava o livro-razão.
			tenantScopedTransaction
				.executeWithoutResult(status -> apply(event, decidedConfident, decidedAnswer, decidedSummary));
		}
		finally {
			TenantContext.clear();
		}
	}

	private record DecisionContext(String tone, String assistantName, BigDecimal threshold,
			List<String> knowledgeChunks, List<AnswerGenerator.ChatTurn> history) {
	}

	private DecisionContext collectContext(ContactMessageRegistered event) {
		if (event.text() == null || event.text().isBlank()) {
			return null; // mídia sem texto: fora do MVP da IA
		}
		if (ledgerRepository.existsByTriggerMessageId(event.messageId())) {
			return null; // reentrega do outbox: decisão já tomada
		}
		Conversation conversation = inboxService.getConversation(event.conversationId());
		if (!conversation.isAvailableForAi()) {
			return null; // humano no comando — a IA não interfere
		}
		AiSettings settings = aiSettingsRepository.findById(event.tenantId())
			.orElseGet(() -> new AiSettings(event.tenantId()));
		if (!settings.isEnabled()) {
			return null;
		}
		List<String> chunks = knowledgeService.retrieveRelevant(event.tenantId(), event.text());
		List<AnswerGenerator.ChatTurn> history = inboxService.recentMessages(event.conversationId())
			.stream()
			.map(message -> new AnswerGenerator.ChatTurn(message.getSender().name(), message.previewText()))
			.toList();
		return new DecisionContext(settings.getTone(), settings.getAssistantName(),
				settings.getConfidenceThreshold(), chunks, history);
	}

	private void apply(ContactMessageRegistered event, boolean confident, AnswerGenerator.GeneratedAnswer answer,
			String handoffSummary) {
		UUID tenantId = event.tenantId();
		if (confident) {
			Message aiMessage = inboxService.recordAiReply(event.conversationId(),
					MessageContent.text(answer.text()), BigDecimal.valueOf(answer.confidence()));
			ledgerRepository.save(ResolutionLedgerEntry.answered(tenantId, event.conversationId(),
					event.messageId(), aiMessage.getId(), answer.confidence()));
		}
		else {
			// cortesia antes do handoff: se a IA preparou um aviso de
			// transferência, o cliente recebe — a promessa é cumprida na linha
			// seguinte, movendo a conversa para a fila humana de verdade
			if (answer.handoffRequested() && answer.text() != null && !answer.text().isBlank()) {
				inboxService.recordAiReply(event.conversationId(), MessageContent.text(answer.text()),
						BigDecimal.valueOf(answer.confidence()));
			}
			inboxService.markNeedsHuman(event.conversationId());
			ledgerRepository.save(ResolutionLedgerEntry.handoff(tenantId, event.conversationId(),
					event.messageId(), answer.confidence(), handoffSummary));
		}
	}
}
