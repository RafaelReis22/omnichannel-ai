package com.omnichannel.inbox.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "conversations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation {

	private static final int PREVIEW_MAX_LENGTH = 140;

	@Id
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "contact_id", nullable = false)
	private UUID contactId;

	@Column(name = "channel_id", nullable = false)
	private UUID channelId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ConversationStatus status;

	@Column(name = "assignee_user_id")
	private UUID assigneeUserId;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false, columnDefinition = "text[]")
	private List<String> tags = new ArrayList<>();

	@Column(name = "last_message_preview")
	private String lastMessagePreview;

	@Column(name = "last_message_at")
	private Instant lastMessageAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public Conversation(UUID tenantId, UUID contactId, UUID channelId) {
		this.id = UUID.randomUUID();
		this.tenantId = tenantId;
		this.contactId = contactId;
		this.channelId = channelId;
		this.status = ConversationStatus.OPEN;
	}

	public boolean isResolved() {
		return status == ConversationStatus.RESOLVED;
	}

	/** Mensagem nova em conversa resolvida reabre — comportamento padrão de helpdesk. */
	public void registerActivity(String preview, Instant at) {
		this.lastMessagePreview = preview == null ? null
				: preview.substring(0, Math.min(preview.length(), PREVIEW_MAX_LENGTH));
		this.lastMessageAt = at;
		if (isResolved()) {
			this.status = ConversationStatus.OPEN;
			this.assigneeUserId = null;
		}
	}

	public void assignTo(UUID userId) {
		this.assigneeUserId = userId;
		this.status = ConversationStatus.WITH_HUMAN;
	}

	/** A IA só assume conversa que não está com humano. */
	public boolean isAvailableForAi() {
		return status == ConversationStatus.OPEN || status == ConversationStatus.WITH_AI;
	}

	public void moveToAi() {
		this.status = ConversationStatus.WITH_AI;
	}

	public void moveToHumanQueue() {
		this.status = ConversationStatus.OPEN;
		this.assigneeUserId = null;
	}

	public void resolve() {
		this.status = ConversationStatus.RESOLVED;
	}

	public void replaceTags(List<String> tags) {
		this.tags = new ArrayList<>(tags);
	}
}
