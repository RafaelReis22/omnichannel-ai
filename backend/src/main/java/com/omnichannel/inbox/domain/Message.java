package com.omnichannel.inbox.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.omnichannel.channels.MessageContent;
import com.omnichannel.channels.MessageKind;

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

@Entity
@Table(name = "messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message {

	@Id
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "conversation_id", nullable = false)
	private UUID conversationId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MessageSender sender;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MessageKind kind;

	@Column
	private String text;

	@Column(name = "media_url")
	private String mediaUrl;

	@Column(name = "mime_type")
	private String mimeType;

	@Column(name = "external_message_id")
	private String externalMessageId;

	@Column(name = "sender_user_id")
	private UUID senderUserId;

	@Column(name = "ai_confidence")
	private BigDecimal aiConfidence;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	private Message(UUID tenantId, UUID conversationId, MessageSender sender, MessageContent content) {
		this.id = UUID.randomUUID();
		this.tenantId = tenantId;
		this.conversationId = conversationId;
		this.sender = sender;
		this.kind = content.kind();
		this.text = content.text();
		this.mediaUrl = content.mediaUrl();
		this.mimeType = content.mimeType();
	}

	public static Message fromContact(UUID tenantId, UUID conversationId, MessageContent content,
			String externalMessageId) {
		Message message = new Message(tenantId, conversationId, MessageSender.CONTACT, content);
		message.externalMessageId = externalMessageId;
		return message;
	}

	public static Message fromAgent(UUID tenantId, UUID conversationId, MessageContent content, UUID agentUserId) {
		Message message = new Message(tenantId, conversationId, MessageSender.AGENT, content);
		message.senderUserId = agentUserId;
		return message;
	}

	public static Message fromAi(UUID tenantId, UUID conversationId, MessageContent content, BigDecimal confidence) {
		Message message = new Message(tenantId, conversationId, MessageSender.AI, content);
		message.aiConfidence = confidence;
		return message;
	}

	/** Texto, ou marcador do tipo de mídia, para o preview da lista. */
	public String previewText() {
		return text != null ? text : "[" + kind.name().toLowerCase() + "]";
	}
}
