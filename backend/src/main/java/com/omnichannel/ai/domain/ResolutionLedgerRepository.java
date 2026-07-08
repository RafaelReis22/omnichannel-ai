package com.omnichannel.ai.domain;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResolutionLedgerRepository extends JpaRepository<ResolutionLedgerEntry, UUID> {

	Page<ResolutionLedgerEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);

	boolean existsByTriggerMessageId(UUID triggerMessageId);
}
