package com.omnichannel.ai.llm;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaddedEmbeddingModelTest {

	private EmbeddingModel fixedModel(float[] vector) {
		return new EmbeddingModel() {
			@Override
			public EmbeddingResponse call(EmbeddingRequest request) {
				return new EmbeddingResponse(List.of(new Embedding(vector, 0)));
			}

			@Override
			public float[] embed(Document document) {
				return vector;
			}
		};
	}

	@Test
	void completaComZerosAteADimensaoDaColuna() {
		var padded = new PaddedEmbeddingModel(fixedModel(new float[] { 1f, 2f }), 5);

		float[] result = padded.embed(new Document("qualquer"));

		assertThat(result).containsExactly(1f, 2f, 0f, 0f, 0f);
		assertThat(padded.dimensions()).isEqualTo(5);
		assertThat(padded.call(new EmbeddingRequest(List.of("x"), null)).getResults().getFirst().getOutput())
			.hasSize(5);
	}

	@Test
	void dimensaoExataPassaIntactaEMaiorFalhaComMensagemClara() {
		float[] exact = { 1f, 2f, 3f };
		assertThat(new PaddedEmbeddingModel(fixedModel(exact), 3).embed(new Document("x"))).isSameAs(exact);

		assertThatThrownBy(() -> new PaddedEmbeddingModel(fixedModel(new float[] { 1f, 2f, 3f, 4f }), 3)
			.embed(new Document("x"))).isInstanceOf(IllegalStateException.class).hasMessageContaining("VECTOR(3)");
	}
}
