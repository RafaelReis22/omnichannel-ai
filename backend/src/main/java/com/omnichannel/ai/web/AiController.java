package com.omnichannel.ai.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.omnichannel.ai.AiSettingsService;
import com.omnichannel.ai.domain.AiSettings;
import com.omnichannel.ai.domain.ResolutionLedgerEntry;
import com.omnichannel.ai.domain.ResolutionLedgerRepository;
import com.omnichannel.shared.tenancy.TenantContext;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Configuração da IA e livro-razão de resolução. */
@RestController
@RequestMapping("/api/ai")
class AiController {

	private static final int MAX_PAGE_SIZE = 100;

	private final AiSettingsService aiSettingsService;
	private final ResolutionLedgerRepository ledgerRepository;
	private final com.omnichannel.ai.ReplySuggestionService replySuggestionService;

	AiController(AiSettingsService aiSettingsService, ResolutionLedgerRepository ledgerRepository,
			com.omnichannel.ai.ReplySuggestionService replySuggestionService) {
		this.aiSettingsService = aiSettingsService;
		this.ledgerRepository = ledgerRepository;
		this.replySuggestionService = replySuggestionService;
	}

	record SuggestionResponse(String suggestion) {
	}

	record SettingsResponse(boolean enabled, BigDecimal confidenceThreshold, String tone, String assistantName) {
		static SettingsResponse from(AiSettings settings) {
			return new SettingsResponse(settings.isEnabled(), settings.getConfidenceThreshold(), settings.getTone(),
					settings.getAssistantName());
		}
	}

	record UpdateSettingsRequest(
			@NotNull Boolean enabled,
			@NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidenceThreshold,
			@Size(max = 500) String tone,
			@Size(max = 80) String assistantName) {
	}

	record LedgerEntryResponse(UUID id, UUID conversationId, String action, BigDecimal confidence,
			String handoffSummary, boolean reviewedByHuman, Instant createdAt) {
		static LedgerEntryResponse from(ResolutionLedgerEntry entry) {
			return new LedgerEntryResponse(entry.getId(), entry.getConversationId(), entry.getAction().name(),
					entry.getConfidence(), entry.getHandoffSummary(), entry.isReviewedByHuman(),
					entry.getCreatedAt());
		}
	}

	@GetMapping("/settings")
	SettingsResponse settings() {
		return SettingsResponse.from(aiSettingsService.settingsOf(TenantContext.getTenantId()));
	}

	@PutMapping("/settings")
	@PreAuthorize("hasRole('ADMIN')")
	SettingsResponse update(@Valid @RequestBody UpdateSettingsRequest request) {
		return SettingsResponse.from(aiSettingsService.update(TenantContext.getTenantId(), request.enabled(),
				request.confidenceThreshold(), request.tone(), request.assistantName()));
	}

	/** Copiloto: rascunho de resposta para o atendente editar — nada é enviado. */
	@PostMapping("/conversations/{conversationId}/suggest")
	SuggestionResponse suggest(@PathVariable UUID conversationId) {
		String suggestion = replySuggestionService.suggest(TenantContext.getTenantId(), conversationId);
		if (suggestion == null) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
					"Sem sugestão para esta conversa no momento");
		}
		return new SuggestionResponse(suggestion);
	}

	@GetMapping("/ledger")
	Page<LedgerEntryResponse> ledger(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size) {
		return ledgerRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE)))
			.map(LedgerEntryResponse::from);
	}

	@PostMapping("/ledger/{id}/review")
	@Transactional
	LedgerEntryResponse review(@PathVariable UUID id) {
		ResolutionLedgerEntry entry = ledgerRepository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado"));
		entry.markReviewed();
		return LedgerEntryResponse.from(entry);
	}
}
