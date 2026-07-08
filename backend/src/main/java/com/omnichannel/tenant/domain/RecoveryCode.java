package com.omnichannel.tenant.domain;

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

/** Código de recuperação de 2FA: hash apenas, uso único. */
@Entity
@Table(name = "recovery_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecoveryCode {

	@Id
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "code_hash", nullable = false, unique = true)
	private String codeHash;

	@Column(name = "used_at")
	private Instant usedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public RecoveryCode(UUID tenantId, UUID userId, String codeHash) {
		this.id = UUID.randomUUID();
		this.tenantId = tenantId;
		this.userId = userId;
		this.codeHash = codeHash;
	}

	public boolean isUsable() {
		return usedAt == null;
	}

	public void markUsed() {
		this.usedAt = Instant.now();
	}
}
