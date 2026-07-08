package com.omnichannel.crm.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.omnichannel.crm.CrmService;
import com.omnichannel.crm.domain.Deal;
import com.omnichannel.crm.domain.PipelineStage;
import com.omnichannel.inbox.domain.Contact;
import com.omnichannel.inbox.domain.ContactRepository;
import com.omnichannel.shared.tenancy.TenantContext;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Pipeline de vendas: board kanban, estágios e negócios. */
@RestController
@RequestMapping("/api/crm")
class CrmController {

	private final CrmService crmService;
	private final ContactRepository contactRepository;

	CrmController(CrmService crmService, ContactRepository contactRepository) {
		this.crmService = crmService;
		this.contactRepository = contactRepository;
	}

	record DealResponse(UUID id, UUID contactId, String contactName, String title, BigDecimal value,
			Instant createdAt) {
	}

	record StageColumn(UUID id, String name, int position, List<DealResponse> deals) {
	}

	record BoardResponse(List<StageColumn> stages) {
	}

	@GetMapping("/board")
	BoardResponse board() {
		UUID tenantId = TenantContext.getTenantId();
		List<PipelineStage> stages = crmService.stagesOf(tenantId);
		List<Deal> deals = crmService.deals();

		Map<UUID, String> contactNames = contactRepository
			.findAllById(deals.stream().map(Deal::getContactId).distinct().toList())
			.stream()
			.collect(Collectors.toMap(Contact::getId, Contact::getName));

		Map<UUID, List<DealResponse>> byStage = deals.stream()
			.map(deal -> Map.entry(deal.getStageId(),
					new DealResponse(deal.getId(), deal.getContactId(),
							contactNames.getOrDefault(deal.getContactId(), "?"), deal.getTitle(), deal.getValue(),
							deal.getCreatedAt())))
			.collect(Collectors.groupingBy(Map.Entry::getKey,
					Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

		return new BoardResponse(stages.stream()
			.map(stage -> new StageColumn(stage.getId(), stage.getName(), stage.getPosition(),
					byStage.getOrDefault(stage.getId(), List.of())))
			.toList());
	}

	record CreateStageRequest(@NotBlank @Size(max = 60) String name) {
	}

	@PostMapping("/stages")
	@ResponseStatus(HttpStatus.CREATED)
	StageColumn createStage(@Valid @RequestBody CreateStageRequest request) {
		PipelineStage stage = crmService.createStage(TenantContext.getTenantId(), request.name());
		return new StageColumn(stage.getId(), stage.getName(), stage.getPosition(), List.of());
	}

	@PutMapping("/stages/{id}")
	StageColumn renameStage(@PathVariable UUID id, @Valid @RequestBody CreateStageRequest request) {
		PipelineStage stage = crmService.renameStage(id, request.name());
		return new StageColumn(stage.getId(), stage.getName(), stage.getPosition(), List.of());
	}

	@DeleteMapping("/stages/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteStage(@PathVariable UUID id) {
		crmService.deleteStage(id);
	}

	record CreateDealRequest(@NotNull UUID contactId, @NotBlank @Size(max = 200) String title, BigDecimal value,
			UUID stageId) {
	}

	record UpdateDealRequest(@NotBlank @Size(max = 200) String title, BigDecimal value) {
	}

	record MoveDealRequest(@NotNull UUID stageId) {
	}

	@PostMapping("/deals")
	@ResponseStatus(HttpStatus.CREATED)
	DealResponse createDeal(@Valid @RequestBody CreateDealRequest request) {
		Deal deal = crmService.createDeal(TenantContext.getTenantId(), request.contactId(), request.title(),
				request.value(), request.stageId());
		return toResponse(deal);
	}

	@PutMapping("/deals/{id}")
	DealResponse updateDeal(@PathVariable UUID id, @Valid @RequestBody UpdateDealRequest request) {
		return toResponse(crmService.updateDeal(id, request.title(), request.value()));
	}

	@PostMapping("/deals/{id}/move")
	DealResponse moveDeal(@PathVariable UUID id, @Valid @RequestBody MoveDealRequest request) {
		return toResponse(crmService.moveDeal(id, request.stageId()));
	}

	@DeleteMapping("/deals/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteDeal(@PathVariable UUID id) {
		crmService.deleteDeal(id);
	}

	private DealResponse toResponse(Deal deal) {
		String contactName = contactRepository.findById(deal.getContactId()).map(Contact::getName).orElse("?");
		return new DealResponse(deal.getId(), deal.getContactId(), contactName, deal.getTitle(), deal.getValue(),
				deal.getCreatedAt());
	}

	@ExceptionHandler(CrmService.CrmNotFoundException.class)
	ProblemDetail handleNotFound(CrmService.CrmNotFoundException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
	}

	@ExceptionHandler(CrmService.StageNotEmptyException.class)
	ProblemDetail handleStageNotEmpty(CrmService.StageNotEmptyException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}
}
