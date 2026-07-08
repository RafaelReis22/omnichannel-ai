package com.omnichannel.ai.domain;

import java.math.BigDecimal;
import java.time.Instant;
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

/**
 * Livro-razão de resolução: TODA decisão autônoma da IA vira um registro
 * auditável — é o diferencial central do produto contra o gap
 * marketing-vs-realidade do mercado (docs/01-visao-e-produto.md). Registro
 * é requisito funcional: teste falha se uma resposta autônoma não gerar
 * a linha correspondente.
 */
@Entity
@Table(name = "resolution_ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResolutionLedgerEntry {

	public enum Action {
		ANSWERED, HANDOFF
	}

	@Id
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "conversation_id", nullable = false)
	private UUID conversationId;

	@Column(name = "trigger_message_id", nullable = false)
	private UUID triggerMessageId;

	@Column(name = "ai_message_id")
	private UUID aiMessageId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Action action;

	@Column(nullable = false)
	private BigDecimal confidence;

	@Column(name = "handoff_summary")
	private String handoffSummary;

	@Column(name = "reviewed_by_human", nullable = false)
	private boolean reviewedByHuman;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	private ResolutionLedgerEntry(UUID tenantId, UUID conversationId, UUID triggerMessageId, Action action,
			double confidence) {
		this.id = UUID.randomUUID();
		this.tenantId = tenantId;
		this.conversationId = conversationId;
		this.triggerMessageId = triggerMessageId;
		this.action = action;
		this.confidence = BigDecimal.valueOf(confidence);
	}

	public static ResolutionLedgerEntry answered(UUID tenantId, UUID conversationId, UUID triggerMessageId,
			UUID aiMessageId, double confidence) {
		ResolutionLedgerEntry entry = new ResolutionLedgerEntry(tenantId, conversationId, triggerMessageId,
				Action.ANSWERED, confidence);
		entry.aiMessageId = aiMessageId;
		return entry;
	}

	public static ResolutionLedgerEntry handoff(UUID tenantId, UUID conversationId, UUID triggerMessageId,
			double confidence, String summary) {
		ResolutionLedgerEntry entry = new ResolutionLedgerEntry(tenantId, conversationId, triggerMessageId,
				Action.HANDOFF, confidence);
		entry.handoffSummary = summary;
		return entry;
	}

	public void markReviewed() {
		this.reviewedByHuman = true;
	}
}
