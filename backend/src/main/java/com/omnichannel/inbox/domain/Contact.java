package com.omnichannel.inbox.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "contacts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Contact {

	@Id
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(nullable = false)
	private String name;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false, columnDefinition = "text[]")
	private List<String> tags = new ArrayList<>();

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public Contact(UUID tenantId, String name) {
		this.id = UUID.randomUUID();
		this.tenantId = tenantId;
		this.name = name;
	}

	public void rename(String name) {
		this.name = name;
	}

	public void replaceTags(List<String> tags) {
		this.tags = new ArrayList<>(tags);
	}
}
