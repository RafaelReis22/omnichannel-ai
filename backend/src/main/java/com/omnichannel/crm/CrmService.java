package com.omnichannel.crm;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.omnichannel.crm.domain.Deal;
import com.omnichannel.crm.domain.DealRepository;
import com.omnichannel.crm.domain.PipelineStage;
import com.omnichannel.crm.domain.PipelineStageRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pipeline de vendas (CRM simples do MVP). Estágios padrão nascem no
 * primeiro acesso do tenant; tudo escopado pelo RLS.
 */
@Service
public class CrmService {

	private static final List<String> DEFAULT_STAGES = List.of("Novo", "Em conversa", "Proposta", "Fechado");

	private final PipelineStageRepository stageRepository;
	private final DealRepository dealRepository;

	public CrmService(PipelineStageRepository stageRepository, DealRepository dealRepository) {
		this.stageRepository = stageRepository;
		this.dealRepository = dealRepository;
	}

	@Transactional
	public List<PipelineStage> stagesOf(UUID tenantId) {
		List<PipelineStage> stages = stageRepository.findAllByOrderByPositionAsc();
		if (!stages.isEmpty()) {
			return stages;
		}
		for (int i = 0; i < DEFAULT_STAGES.size(); i++) {
			stageRepository.save(new PipelineStage(tenantId, DEFAULT_STAGES.get(i), i));
		}
		return stageRepository.findAllByOrderByPositionAsc();
	}

	@Transactional
	public PipelineStage createStage(UUID tenantId, String name) {
		int position = stageRepository.countByTenantId(tenantId);
		return stageRepository.save(new PipelineStage(tenantId, name, position));
	}

	@Transactional
	public PipelineStage renameStage(UUID stageId, String name) {
		PipelineStage stage = stageRepository.findById(stageId)
			.orElseThrow(() -> new CrmNotFoundException("Estágio não encontrado"));
		stage.rename(name);
		return stage;
	}

	@Transactional
	public void deleteStage(UUID stageId) {
		if (dealRepository.existsByStageId(stageId)) {
			throw new StageNotEmptyException();
		}
		stageRepository.deleteById(stageId);
	}

	@Transactional(readOnly = true)
	public List<Deal> deals() {
		return dealRepository.findAllByOrderByCreatedAtDesc();
	}

	@Transactional
	public Deal createDeal(UUID tenantId, UUID contactId, String title, BigDecimal value, UUID stageId) {
		UUID targetStage = stageId != null ? stageId : stagesOf(tenantId).getFirst().getId();
		return dealRepository.save(new Deal(tenantId, contactId, targetStage, title, value));
	}

	@Transactional
	public Deal moveDeal(UUID dealId, UUID stageId) {
		Deal deal = requireDeal(dealId);
		stageRepository.findById(stageId).orElseThrow(() -> new CrmNotFoundException("Estágio não encontrado"));
		deal.moveTo(stageId);
		return deal;
	}

	@Transactional
	public Deal updateDeal(UUID dealId, String title, BigDecimal value) {
		Deal deal = requireDeal(dealId);
		deal.update(title, value);
		return deal;
	}

	@Transactional
	public void deleteDeal(UUID dealId) {
		dealRepository.delete(requireDeal(dealId));
	}

	private Deal requireDeal(UUID dealId) {
		return dealRepository.findById(dealId)
			.orElseThrow(() -> new CrmNotFoundException("Negócio não encontrado"));
	}

	public static class CrmNotFoundException extends RuntimeException {
		public CrmNotFoundException(String message) {
			super(message);
		}
	}

	public static class StageNotEmptyException extends RuntimeException {
		public StageNotEmptyException() {
			super("Estágio possui negócios — mova-os antes de excluir");
		}
	}
}
