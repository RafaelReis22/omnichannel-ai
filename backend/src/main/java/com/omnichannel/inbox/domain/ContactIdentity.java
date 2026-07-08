package com.omnichannel.inbox.domain;

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

/** Identidade de um contato em um canal — chave de deduplicação na entrada. */
@Entity
@Table(name = "contact_identities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContactIdentity {

	@Id
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "contact_id", nullable = false)
	private UUID contactId;

	@Enumerated(EnumType.STRING)
	@Column(name = "channel_type", nullable = false)
	private ChannelType channelType;

	@Column(name = "external_id", nullable = false)
	private String externalId;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public ContactIdentity(UUID tenantId, UUID contactId, ChannelType channelType, String externalId) {
		this.id = UUID.randomUUID();
		this.tenantId = tenantId;
		this.contactId = contactId;
		this.channelType = channelType;
		this.externalId = externalId;
	}
}
