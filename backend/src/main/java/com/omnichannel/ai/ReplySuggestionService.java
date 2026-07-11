package com.omnichannel.ai;

import java.util.List;
import java.util.UUID;

import com.omnichannel.ai.domain.AiSettings;
import com.omnichannel.ai.domain.AiSettingsRepository;
import com.omnichannel.inbox.InboxService;
import com.omnichannel.inbox.domain.Message;

import org.springframework.stereotype.Service;

/**
 * Copiloto do atendente: gera um RASCUNHO de resposta para a conversa que
 * está com um humano. Nada é enviado nem persistido — o atendente edita e
 * decide. Por isso não passa pelo portão de confiança nem entra no
 * livro-razão (a decisão final é humana).
 */
@Service
public class ReplySuggestionService {

	private final AnswerGenerator answerGenerator;
	private final KnowledgeService knowledgeService;
	private final InboxService inboxService;
	private final AiSettingsRepository aiSettingsRepository;

	public ReplySuggestionService(AnswerGenerator answerGenerator, KnowledgeService knowledgeService,
			InboxService inboxService, AiSettingsRepository aiSettingsRepository) {
		this.answerGenerator = answerGenerator;
		this.knowledgeService = knowledgeService;
		this.inboxService = inboxService;
		this.aiSettingsRepository = aiSettingsRepository;
	}

	/** Rascunho para a última mensagem do cliente; null se não há o que sugerir. */
	public String suggest(UUID tenantId, UUID conversationId) {
		List<Message> recent = inboxService.recentMessages(conversationId);
		String lastFromContact = recent.reversed()
			.stream()
			.filter(message -> message.getSender() == com.omnichannel.inbox.domain.MessageSender.CONTACT)
			.map(Message::previewText)
			.findFirst()
			.orElse(null);
		if (lastFromContact == null) {
			return null;
		}
		AiSettings settings = aiSettingsRepository.findById(tenantId).orElseGet(() -> new AiSettings(tenantId));
		List<String> chunks = knowledgeService.retrieveRelevant(tenantId, lastFromContact);
		List<AnswerGenerator.ChatTurn> history = recent.stream()
			.map(message -> new AnswerGenerator.ChatTurn(message.getSender().name(), message.previewText()))
			.toList();
		// chamada de LLM fora de transação (cada leitura acima teve a sua)
		AnswerGenerator.GeneratedAnswer answer = answerGenerator.generate(new AnswerGenerator.AnswerRequest(
				settings.getTone(), settings.getAssistantName(), chunks, history, lastFromContact));
		return answer.text() == null || answer.text().isBlank() ? null : answer.text();
	}
}
