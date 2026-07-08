package com.omnichannel.ai.domain;

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

/** Documento da base de conhecimento — a fonte do RAG. */
@Entity
@Table(name = "kb_documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KbDocument {

	@Id
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String content;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public KbDocument(UUID tenantId, String title, String content) {
		this.id = UUID.randomUUID();
		this.tenantId = tenantId;
		this.title = title;
		this.content = content;
	}
}
