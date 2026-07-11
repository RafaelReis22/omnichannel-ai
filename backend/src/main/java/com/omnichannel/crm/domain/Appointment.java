package com.omnichannel.crm.domain;

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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "appointments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Appointment {

	public enum Status {

		SCHEDULED, DONE, CANCELLED

	}

	@Id
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "contact_id", nullable = false)
	private UUID contactId;

	@Column(name = "conversation_id")
	private UUID conversationId;

	@Column(name = "created_by_user_id", nullable = false)
	private UUID createdByUserId;

	@Column(nullable = false)
	private String title;

	@Column
	private String notes;

	@Column(name = "scheduled_at", nullable = false)
	private Instant scheduledAt;

	@Column(name = "duration_minutes", nullable = false)
	private int durationMinutes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public Appointment(UUID tenantId, UUID contactId, UUID conversationId, UUID createdByUserId, String title,
			String notes, Instant scheduledAt, int durationMinutes) {
		this.id = UUID.randomUUID();
		this.tenantId = tenantId;
		this.contactId = contactId;
		this.conversationId = conversationId;
		this.createdByUserId = createdByUserId;
		this.title = title;
		this.notes = notes;
		this.scheduledAt = scheduledAt;
		this.durationMinutes = durationMinutes;
		this.status = Status.SCHEDULED;
	}

	public void markDone() {
		this.status = Status.DONE;
	}

	public void cancel() {
		this.status = Status.CANCELLED;
	}
}
