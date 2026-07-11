package com.omnichannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import com.omnichannel.ai.AnswerGenerator;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Infraestrutura de teste compartilhada por toda a suíte: containers reais
 * (Postgres+pgvector, RabbitMQ, Redis) e fakes de IA — o CI nunca chama
 * LLM real (docs/08-testes.md).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer<?> postgresContainer() {
		return new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
	}

	@Bean
	@ServiceConnection
	RabbitMQContainer rabbitContainer() {
		return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-alpine"));
	}

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
	}

	@Bean
	MinIOContainer minioContainer() {
		return new MinIOContainer(DockerImageName.parse("minio/minio:latest"));
	}

	@Bean
	DynamicPropertyRegistrar storageProperties(MinIOContainer minio) {
		return registry -> {
			registry.add("app.storage.endpoint", minio::getS3URL);
			registry.add("app.storage.access-key-id", minio::getUserName);
			registry.add("app.storage.secret-access-key", minio::getPassword);
			registry.add("app.storage.bucket", () -> "test-media");
		};
	}

	// --- Fakes de IA (modelos reais desligados via spring.ai.model.*=none) ---

	@Bean
	EmbeddingModel fakeEmbeddingModel() {
		return new FakeEmbeddingModel();
	}

	@Bean
	@org.springframework.context.annotation.Primary
	AnswerGenerator fakeAnswerGenerator() {
		return new FakeAnswerGenerator();
	}

	/** Vetores determinísticos por hash do texto — mesmo texto, mesmo vetor. */
	static class FakeEmbeddingModel implements EmbeddingModel {

		private static final int DIMENSIONS = 1536;

		@Override
		public EmbeddingResponse call(EmbeddingRequest request) {
			List<Embedding> embeddings = new ArrayList<>();
			int index = 0;
			for (String text : request.getInstructions()) {
				embeddings.add(new Embedding(vectorFor(text), index++));
			}
			return new EmbeddingResponse(embeddings);
		}

		@Override
		public float[] embed(Document document) {
			return vectorFor(document.getText());
		}

		private float[] vectorFor(String text) {
			Random random = new Random(text == null ? 0 : text.hashCode());
			float[] vector = new float[DIMENSIONS];
			double norm = 0;
			for (int i = 0; i < DIMENSIONS; i++) {
				vector[i] = random.nextFloat() - 0.5f;
				norm += vector[i] * vector[i];
			}
			float scale = (float) (1.0 / Math.sqrt(norm));
			for (int i = 0; i < DIMENSIONS; i++) {
				vector[i] *= scale;
			}
			return vector;
		}
	}

	/** Resposta e confiança controláveis pelo teste; captura as requisições. */
	public static class FakeAnswerGenerator implements AnswerGenerator {

		public static volatile double nextConfidence = 0.95;
		public static volatile String nextAnswer = "resposta-de-teste";
		public static volatile boolean nextHandoffRequested = false;
		public static volatile boolean nextBuyingIntent = false;
		public static final List<AnswerRequest> received = new CopyOnWriteArrayList<>();

		public static void reset() {
			nextConfidence = 0.95;
			nextAnswer = "resposta-de-teste";
			nextHandoffRequested = false;
			nextBuyingIntent = false;
			received.clear();
		}

		@Override
		public GeneratedAnswer generate(AnswerRequest request) {
			received.add(request);
			return new GeneratedAnswer(nextAnswer, nextConfidence, nextHandoffRequested, nextBuyingIntent);
		}

		@Override
		public String summarize(List<ChatTurn> history) {
			return "resumo-de-teste (" + history.size() + " turnos)";
		}
	}
}
