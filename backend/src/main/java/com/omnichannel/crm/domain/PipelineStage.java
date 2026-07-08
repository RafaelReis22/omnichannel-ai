package com.omnichannel.crm.domain;

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

@Entity
@Table(name = "pipeline_stages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PipelineStage {

	@Id
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private int position;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public PipelineStage(UUID tenantId, String name, int position) {
		this.id = UUID.randomUUID();
		this.tenantId = tenantId;
		this.name = name;
		this.position = position;
	}

	public void rename(String name) {
		this.name = name;
	}
}
