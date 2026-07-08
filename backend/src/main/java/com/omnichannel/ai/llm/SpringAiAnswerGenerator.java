package com.omnichannel.ai.llm;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnichannel.ai.AnswerGenerator;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Implementação de produção da porta {@link AnswerGenerator}: Claude via
 * Spring AI. A confiança vem em saída JSON estruturada — auto-reportada,
 * como é padrão do setor; a honestidade do produto está no livro-razão,
 * não na calibração do número.
 *
 * <p>{@code @Lazy}: nos testes um fake {@code @Primary} satisfaz a porta e
 * este bean (que exige um ChatModel real) nunca chega a ser instanciado.
 */
@Component
@Lazy
class SpringAiAnswerGenerator implements AnswerGenerator {

	private static final String SYSTEM_PROMPT = """
			Você é um atendente de suporte ao cliente. Responda em português brasileiro, \
			com objetividade e cordialidade.%s

			REGRAS OBRIGATÓRIAS:
			1. Use APENAS as informações da BASE DE CONHECIMENTO abaixo para responder. \
			Não invente fatos, preços, prazos ou políticas.
			2. Avalie sua confiança de 0.0 a 1.0: 1.0 = a base cobre a pergunta \
			completamente; abaixo de 0.5 = a base não cobre ou a pergunta exige um humano \
			(reclamação grave, negociação, dado pessoal).
			3. Responda SOMENTE com JSON válido: {"resposta": "...", "confianca": 0.0}

			BASE DE CONHECIMENTO:
			%s""";

	private final ChatClient chatClient;
	private final ObjectMapper objectMapper;

	SpringAiAnswerGenerator(ChatModel chatModel, ObjectMapper objectMapper) {
		this.chatClient = ChatClient.create(chatModel);
		this.objectMapper = objectMapper;
	}

	private record ModelOutput(String resposta, double confianca) {
	}

	@Override
	public GeneratedAnswer generate(AnswerRequest request) {
		String toneClause = request.tenantTone() == null ? ""
				: " Tom de voz da empresa: " + request.tenantTone() + ".";
		String knowledge = request.knowledgeChunks().isEmpty() ? "(vazia)"
				: String.join("\n---\n", request.knowledgeChunks());
		String conversation = request.history()
			.stream()
			.map(turn -> turn.role() + ": " + turn.text())
			.reduce((a, b) -> a + "\n" + b)
			.orElse("");

		String raw = chatClient.prompt()
			.system(SYSTEM_PROMPT.formatted(toneClause, knowledge))
			.user("HISTÓRICO:\n" + conversation + "\n\nÚLTIMA MENSAGEM DO CLIENTE:\n" + request.question())
			.call()
			.content();
		return parse(raw);
	}

	@Override
	public String summarize(List<ChatTurn> history) {
		String conversation = history.stream()
			.map(turn -> turn.role() + ": " + turn.text())
			.reduce((a, b) -> a + "\n" + b)
			.orElse("");
		return chatClient.prompt()
			.system("Resuma a conversa a seguir em até 3 frases, em português, para um atendente humano "
					+ "assumir com contexto. Inclua o que o cliente quer e o que já foi tentado.")
			.user(conversation)
			.call()
			.content();
	}

	private GeneratedAnswer parse(String raw) {
		try {
			// modelos às vezes cercam o JSON com texto/markdown — extrai o objeto
			int start = raw.indexOf('{');
			int end = raw.lastIndexOf('}');
			ModelOutput output = objectMapper.readValue(raw.substring(start, end + 1), ModelOutput.class);
			double bounded = Math.clamp(output.confianca(), 0.0, 1.0);
			return new GeneratedAnswer(output.resposta(), bounded);
		}
		catch (Exception e) {
			// saída fora do contrato = confiança zero: cai no handoff, nunca
			// manda lixo para o cliente
			return new GeneratedAnswer(null, 0.0);
		}
	}
}
