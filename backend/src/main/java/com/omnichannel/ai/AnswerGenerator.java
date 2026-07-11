package com.omnichannel.ai;

import java.util.List;

/**
 * Porta para o LLM. A implementação de produção usa Spring AI (Claude);
 * testes usam um fake determinístico — o CI nunca chama LLM real
 * (docs/08-testes.md). A confiança é auto-reportada pelo modelo em saída
 * estruturada; a auditabilidade real vem do livro-razão, não do número.
 */
public interface AnswerGenerator {

	GeneratedAnswer generate(AnswerRequest request);

	/** Resumo curto da conversa para o humano assumir com contexto no handoff. */
	String summarize(List<ChatTurn> history);

	record AnswerRequest(String tenantTone, String assistantName, List<String> knowledgeChunks,
			List<ChatTurn> history, String question) {
	}

	record ChatTurn(String role, String text) {
	}

	/**
	 * {@code handoffRequested}: o cliente pediu explicitamente um humano — transferir de verdade.
	 * {@code buyingIntent}: o cliente demonstrou intenção de compra — etiquetar para o comercial.
	 */
	record GeneratedAnswer(String text, double confidence, boolean handoffRequested, boolean buyingIntent) {

		public GeneratedAnswer(String text, double confidence) {
			this(text, confidence, false, false);
		}

		public GeneratedAnswer(String text, double confidence, boolean handoffRequested) {
			this(text, confidence, handoffRequested, false);
		}
	}
}
