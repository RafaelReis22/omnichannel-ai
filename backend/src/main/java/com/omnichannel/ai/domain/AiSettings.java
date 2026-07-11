package com.omnichannel.ai.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.UpdateTimestamp;

/** Política de autonomia da IA por tenant (padrão Strategy — docs/03). */
@Entity
@Table(name = "ai_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiSettings {

	public static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("0.700");

	@Id
	@Column(name = "tenant_id")
	private UUID tenantId;

	@Column(nullable = false)
	private boolean enabled;

	@Column(name = "confidence_threshold", nullable = false)
	private BigDecimal confidenceThreshold;

	@Column
	private String tone;

	@Column(name = "assistant_name")
	private String assistantName;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public AiSettings(UUID tenantId) {
		this.tenantId = tenantId;
		this.enabled = true;
		this.confidenceThreshold = DEFAULT_THRESHOLD;
	}

	public void update(boolean enabled, BigDecimal confidenceThreshold, String tone, String assistantName) {
		this.enabled = enabled;
		this.confidenceThreshold = confidenceThreshold;
		this.tone = tone;
		this.assistantName = assistantName;
	}

	public boolean allowsAutonomousAnswer(double confidence) {
		return BigDecimal.valueOf(confidence).compareTo(confidenceThreshold) >= 0;
	}
}
