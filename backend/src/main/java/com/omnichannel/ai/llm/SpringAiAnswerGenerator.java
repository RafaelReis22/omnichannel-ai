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
			%s Responda em português brasileiro, \
			com objetividade e cordialidade, como uma pessoa real digitando — frases \
			naturais, sem soar robótico ou formal demais.%s

			REGRAS OBRIGATÓRIAS:
			1. Use APENAS as informações da BASE DE CONHECIMENTO abaixo para responder. \
			Não invente fatos, preços, prazos ou políticas.
			2. Avalie sua confiança de 0.0 a 1.0: 1.0 = a base cobre a pergunta \
			completamente; abaixo de 0.5 = a base não cobre ou a pergunta exige um humano \
			(reclamação grave, negociação, dado pessoal).
			3. Se o cliente pedir para falar com uma pessoa/humano/responsável/atendente, \
			ou exigir claramente alguém, marque "transferir": true e escreva em "resposta" \
			um aviso curto e cordial de que você vai passar para alguém da equipe.
			4. Nunca afirme ser uma IA por conta própria; se o cliente perguntar \
			diretamente se é um robô, seja honesto.
			5. Responda SOMENTE com JSON válido: \
			{"resposta": "...", "confianca": 0.0, "transferir": false}

			BASE DE CONHECIMENTO:
			%s""";

	private final ChatClient chatClient;
	private final ObjectMapper objectMapper;

	SpringAiAnswerGenerator(ChatModel chatModel, ObjectMapper objectMapper) {
		this.chatClient = ChatClient.create(chatModel);
		this.objectMapper = objectMapper;
	}

	private record ModelOutput(String resposta, double confianca, Boolean transferir) {
	}

	@Override
	public GeneratedAnswer generate(AnswerRequest request) {
		String persona = request.assistantName() == null || request.assistantName().isBlank()
				? "Você é um atendente de suporte ao cliente."
				: "Você é " + request.assistantName()
						+ ", da equipe de atendimento — fale em primeira pessoa, como " + request.assistantName()
						+ ".";
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
			.system(SYSTEM_PROMPT.formatted(persona, toneClause, knowledge))
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
			return new GeneratedAnswer(output.resposta(), bounded, Boolean.TRUE.equals(output.transferir()));
		}
		catch (Exception e) {
			// saída fora do contrato = confiança zero: cai no handoff, nunca
			// manda lixo para o cliente
			return new GeneratedAnswer(null, 0.0);
		}
	}
}
