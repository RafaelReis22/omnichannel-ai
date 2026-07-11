package com.omnichannel.ai.llm;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Modo de desenvolvimento sem custo ({@code AI_EMBEDDING_PROVIDER=ollama},
 * docs/10-teste-sem-custo.md): os modelos de embedding locais têm dimensão
 * menor que a coluna do vector store, então o modelo do Ollama entra
 * embrulhado no {@link PaddedEmbeddingModel}.
 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.model.embedding", havingValue = "ollama")
class OllamaEmbeddingCompatConfig {

	@Bean
	@Primary
	EmbeddingModel paddedOllamaEmbeddingModel(OllamaEmbeddingModel ollamaEmbeddingModel,
			@Value("${spring.ai.vectorstore.pgvector.dimensions:1536}") int dimensions) {
		return new PaddedEmbeddingModel(ollamaEmbeddingModel, dimensions);
	}
}
