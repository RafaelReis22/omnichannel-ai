package com.omnichannel.ai.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.omnichannel.ai.KnowledgeService;
import com.omnichannel.ai.domain.KbDocument;
import com.omnichannel.shared.tenancy.TenantContext;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Base de conhecimento do RAG — escrita restrita a Admin/Manager. */
@RestController
@RequestMapping("/api/knowledge")
class KnowledgeController {

	private final KnowledgeService knowledgeService;

	KnowledgeController(KnowledgeService knowledgeService) {
		this.knowledgeService = knowledgeService;
	}

	record CreateDocumentRequest(@NotBlank @Size(max = 200) String title,
			@NotBlank @Size(max = 200_000) String content) {
	}

	record DocumentResponse(UUID id, String title, Instant createdAt) {
		static DocumentResponse from(KbDocument document) {
			return new DocumentResponse(document.getId(), document.getTitle(), document.getCreatedAt());
		}
	}

	@PostMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
	@ResponseStatus(HttpStatus.CREATED)
	DocumentResponse create(@Valid @RequestBody CreateDocumentRequest request) {
		return DocumentResponse
			.from(knowledgeService.addDocument(TenantContext.getTenantId(), request.title(), request.content()));
	}

	@GetMapping
	List<DocumentResponse> list() {
		return knowledgeService.list().stream().map(DocumentResponse::from).toList();
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable UUID id) {
		knowledgeService.delete(id);
	}
}
