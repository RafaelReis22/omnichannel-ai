package com.omnichannel.inbox.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.CreationTimestamp;

/** Nota interna da equipe em uma conversa — nunca visível ao contato. */
@Entity
@Table(name = "conversation_notes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationNote {

	@Id
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "conversation_id", nullable = false)
	private UUID conversationId;

	@Column(name = "author_user_id", nullable = false)
	private UUID authorUserId;

	@Column(nullable = false)
	private String text;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public ConversationNote(UUID tenantId, UUID conversationId, UUID authorUserId, String text) {
		this.id = UUID.randomUUID();
		this.tenantId = tenantId;
		this.conversationId = conversationId;
		this.authorUserId = authorUserId;
		this.text = text;
	}
}
