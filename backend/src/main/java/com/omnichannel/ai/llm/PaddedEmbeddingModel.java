package com.omnichannel.ai.llm;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Completa com zeros embeddings de dimensão menor que a da coluna
 * {@code VECTOR(n)} do Flyway (V8) — ex.: nomic-embed-text produz 768 e a
 * coluna é 1536. Zero-padding não altera a similaridade de cosseno entre
 * vetores do mesmo modelo, então o ranking do retrieval fica idêntico;
 * só permite trocar de provedor sem migrar o schema.
 */
class PaddedEmbeddingModel implements EmbeddingModel {

	private final EmbeddingModel delegate;
	private final int dimensions;

	PaddedEmbeddingModel(EmbeddingModel delegate, int dimensions) {
		this.delegate = delegate;
		this.dimensions = dimensions;
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		EmbeddingResponse response = delegate.call(request);
		List<Embedding> padded = response.getResults()
			.stream()
			.map(embedding -> new Embedding(pad(embedding.getOutput()), embedding.getIndex()))
			.toList();
		return new EmbeddingResponse(padded, response.getMetadata());
	}

	@Override
	public float[] embed(Document document) {
		return pad(delegate.embed(document));
	}

	@Override
	public int dimensions() {
		return dimensions;
	}

	private float[] pad(float[] vector) {
		if (vector.length == dimensions) {
			return vector;
		}
		if (vector.length > dimensions) {
			throw new IllegalStateException("Modelo de embedding produz %d dimensões — maior que a coluna VECTOR(%d); use um modelo menor ou migre o schema"
				.formatted(vector.length, dimensions));
		}
		float[] padded = new float[dimensions];
		System.arraycopy(vector, 0, padded, 0, vector.length);
		return padded;
	}
}
