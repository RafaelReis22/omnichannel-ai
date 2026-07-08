package com.omnichannel.ai.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KbDocumentRepository extends JpaRepository<KbDocument, UUID> {
}
