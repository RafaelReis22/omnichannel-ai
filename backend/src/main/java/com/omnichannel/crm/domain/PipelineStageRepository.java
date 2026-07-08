package com.omnichannel.crm.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineStageRepository extends JpaRepository<PipelineStage, UUID> {

	List<PipelineStage> findAllByOrderByPositionAsc();

	int countByTenantId(UUID tenantId);
}
