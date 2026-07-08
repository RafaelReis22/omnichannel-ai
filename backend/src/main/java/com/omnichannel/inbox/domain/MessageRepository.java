package com.omnichannel.inbox.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, UUID> {

	Page<Message> findByConversationIdOrderByCreatedAtAsc(UUID conversationId, Pageable pageable);

	List<Message> findTop50ByConversationIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(UUID conversationId,
			Instant after);

	boolean existsByExternalMessageId(String externalMessageId);
}
