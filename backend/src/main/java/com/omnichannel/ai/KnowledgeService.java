package com.omnichannel.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.omnichannel.ai.domain.KbDocument;
import com.omnichannel.ai.domain.KbDocumentRepository;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base de conhecimento (RAG): documentos são divididos em chunks,
 * embarcados e gravados no pgvector com o tenant no metadata — o RLS
 * isola os vetores no banco (migration V8), e o filtro de metadata é a
 * segunda cerca na consulta.
 */
@Service
public class KnowledgeService {

	private static final int TOP_K = 4;

	private final KbDocumentRepository kbDocumentRepository;
	private final VectorStore vectorStore;
	private final JdbcTemplate jdbcTemplate;

	public KnowledgeService(KbDocumentRepository kbDocumentRepository, VectorStore vectorStore,
			JdbcTemplate jdbcTemplate) {
		this.kbDocumentRepository = kbDocumentRepository;
		this.vectorStore = vectorStore;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public KbDocument addDocument(UUID tenantId, String title, String content) {
		KbDocument document = kbDocumentRepository.save(new KbDocument(tenantId, title, content));
		List<Document> chunks = chunksOf(document);
		vectorStore.add(chunks);
		return document;
	}

	@Transactional(readOnly = true)
	public List<KbDocument> list() {
		return kbDocumentRepository.findAll();
	}

	@Transactional
	public void delete(UUID documentId) {
		kbDocumentRepository.deleteById(documentId);
		// vetores são nossos (tabela da V8): remoção direta, escopada pelo RLS
		jdbcTemplate.update("DELETE FROM vector_store WHERE metadata ->> 'kb_document_id' = ?",
				documentId.toString());
	}

	/** Chunks mais relevantes para a pergunta, dentro do tenant corrente. */
	@Transactional(readOnly = true)
	public List<String> retrieveRelevant(UUID tenantId, String question) {
		SearchRequest request = SearchRequest.builder()
			.query(question)
			.topK(TOP_K)
			.filterExpression("tenant_id == '" + tenantId + "'")
			.build();
		return vectorStore.similaritySearch(request).stream().map(Document::getText).toList();
	}

	private List<Document> chunksOf(KbDocument document) {
		List<String> parts = com.omnichannel.ai.domain.KnowledgeChunker.chunk(document.getContent());
		return parts.stream()
			.map(part -> new Document(part, Map.of(
					"tenant_id", document.getTenantId().toString(),
					"kb_document_id", document.getId().toString(),
					"title", document.getTitle())))
			.toList();
	}
}
