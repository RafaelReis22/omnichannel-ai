package com.omnichannel.crm.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DealRepository extends JpaRepository<Deal, UUID> {

	List<Deal> findAllByOrderByCreatedAtDesc();

	boolean existsByStageId(UUID stageId);
}
