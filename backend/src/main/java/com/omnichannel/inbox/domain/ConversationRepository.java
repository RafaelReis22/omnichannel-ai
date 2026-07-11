package com.omnichannel.inbox.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

	/** Conversa "viva" (não resolvida) de um contato em um canal, se houver. */
	Optional<Conversation> findFirstByContactIdAndChannelIdAndStatusNotOrderByCreatedAtDesc(UUID contactId,
			UUID channelId, ConversationStatus status);

	Optional<Conversation> findFirstByContactIdAndChannelIdOrderByLastMessageAtDesc(UUID contactId, UUID channelId);

	List<Conversation> findAllByContactIdOrderByLastMessageAtDesc(UUID contactId);

	@Query("""
			SELECT c FROM Conversation c
			WHERE c.archivedAt IS NULL
			  AND (:status IS NULL OR c.status = :status)
			  AND (:assigneeUserId IS NULL OR c.assigneeUserId = :assigneeUserId)
			ORDER BY c.lastMessageAt DESC NULLS LAST
			""")
	Page<Conversation> search(@Param("status") ConversationStatus status,
			@Param("assigneeUserId") UUID assigneeUserId, Pageable pageable);
}
