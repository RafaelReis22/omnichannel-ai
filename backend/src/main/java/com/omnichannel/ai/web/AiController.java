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

	AiController(AiSettingsService aiSettingsService, ResolutionLedgerRepository ledgerRepository) {
		this.aiSettingsService = aiSettingsService;
		this.ledgerRepository = ledgerRepository;
	}

	record SettingsResponse(boolean enabled, BigDecimal confidenceThreshold, String tone) {
		static SettingsResponse from(AiSettings settings) {
			return new SettingsResponse(settings.isEnabled(), settings.getConfidenceThreshold(), settings.getTone());
		}
	}

	record UpdateSettingsRequest(
			@NotNull Boolean enabled,
			@NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidenceThreshold,
			@Size(max = 500) String tone) {
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
				request.confidenceThreshold(), request.tone()));
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
