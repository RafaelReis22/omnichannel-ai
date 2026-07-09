package com.omnichannel.inbox.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationNoteRepository extends JpaRepository<ConversationNote, UUID> {

	List<ConversationNote> findAllByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}
