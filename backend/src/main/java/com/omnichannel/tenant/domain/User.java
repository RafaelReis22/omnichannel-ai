package com.omnichannel.tenant.domain;

import java.time.Instant;
import java.util.Locale;
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

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UserRole role;

	@Column(name = "totp_secret_encrypted")
	private String totpSecretEncrypted;

	@Column(name = "totp_enabled", nullable = false)
	private boolean totpEnabled;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public User(UUID tenantId, String name, String email, String passwordHash, UserRole role) {
		this.id = UUID.randomUUID();
		this.tenantId = tenantId;
		this.name = name;
		this.email = normalizeEmail(email);
		this.passwordHash = passwordHash;
		this.role = role;
	}

	public static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	/** Guarda o segredo enrolado; o 2FA só passa a valer no confirm. */
	public void startTotpEnrollment(String totpSecretEncrypted) {
		this.totpSecretEncrypted = totpSecretEncrypted;
		this.totpEnabled = false;
	}

	public void confirmTotpEnrollment() {
		if (totpSecretEncrypted == null) {
			throw new IllegalStateException("Nenhum enrolamento de TOTP em andamento");
		}
		this.totpEnabled = true;
	}
}
