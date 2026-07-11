package com.omnichannel.channels.domain;

import java.time.Instant;
import java.util.UUID;

import com.omnichannel.channels.ChannelType;

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
@Table(name = "channels")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Channel {

	@Id
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ChannelType type;

	@Column(nullable = false)
	private String name;

	@Column(name = "credentials_encrypted", nullable = false)
	private String credentialsEncrypted;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ChannelStatus status;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public Channel(UUID tenantId, ChannelType type, String name, String credentialsEncrypted) {
		this.id = UUID.randomUUID();
		this.tenantId = tenantId;
		this.type = type;
		this.name = name;
		this.credentialsEncrypted = credentialsEncrypted;
		this.status = ChannelStatus.ACTIVE;
	}

	public boolean isActive() {
		return status == ChannelStatus.ACTIVE;
	}

	public void activate() {
		this.status = ChannelStatus.ACTIVE;
	}

	public void disable() {
		this.status = ChannelStatus.DISABLED;
	}
}
