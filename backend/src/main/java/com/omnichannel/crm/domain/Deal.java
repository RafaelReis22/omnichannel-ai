package com.omnichannel.crm.domain;

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

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "deals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Deal {

	@Id
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "contact_id", nullable = false)
	private UUID contactId;

	@Column(name = "stage_id", nullable = false)
	private UUID stageId;

	@Column(nullable = false)
	private String title;

	@Column
	private BigDecimal value;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public Deal(UUID tenantId, UUID contactId, UUID stageId, String title, BigDecimal value) {
		this.id = UUID.randomUUID();
		this.tenantId = tenantId;
		this.contactId = contactId;
		this.stageId = stageId;
		this.title = title;
		this.value = value;
	}

	public void moveTo(UUID stageId) {
		this.stageId = stageId;
	}

	public void update(String title, BigDecimal value) {
		this.title = title;
		this.value = value;
	}
}
