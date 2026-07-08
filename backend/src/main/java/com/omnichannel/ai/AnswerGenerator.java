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

	record AnswerRequest(String tenantTone, List<String> knowledgeChunks, List<ChatTurn> history, String question) {
	}

	record ChatTurn(String role, String text) {
	}

	record GeneratedAnswer(String text, double confidence) {
	}
}
