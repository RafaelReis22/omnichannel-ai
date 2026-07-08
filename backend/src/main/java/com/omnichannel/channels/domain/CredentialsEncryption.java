package com.omnichannel.channels.domain;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM para credenciais de canal em repouso (docs/06-seguranca.md).
 * Formato: base64(iv de 12 bytes || ciphertext+tag). Chave via
 * CHANNEL_CREDENTIALS_KEY (base64 de 32 bytes) — rotação de chave exige
 * re-criptografar os canais existentes (processo em docs/06, rotação de
 * segredos).
 */
@Component
class CredentialsEncryption {

	private static final int KEY_BYTES = 32;
	private static final int IV_BYTES = 12;
	private static final int GCM_TAG_BITS = 128;

	private final SecretKeySpec key;
	private final SecureRandom secureRandom = new SecureRandom();

	CredentialsEncryption(@Value("${app.channels.credentials-key:}") String base64Key) {
		byte[] keyBytes;
		try {
			keyBytes = Base64.getDecoder().decode(base64Key);
		}
		catch (IllegalArgumentException e) {
			keyBytes = new byte[0];
		}
		if (keyBytes.length != KEY_BYTES) {
			throw new IllegalStateException(
					"CHANNEL_CREDENTIALS_KEY ausente ou inválida (esperado: base64 de %d bytes). Gere com: openssl rand -base64 %d"
						.formatted(KEY_BYTES, KEY_BYTES));
		}
		this.key = new SecretKeySpec(keyBytes, "AES");
	}

	String encrypt(String plaintext) {
		try {
			byte[] iv = new byte[IV_BYTES];
			secureRandom.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
			byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			byte[] payload = new byte[IV_BYTES + ciphertext.length];
			System.arraycopy(iv, 0, payload, 0, IV_BYTES);
			System.arraycopy(ciphertext, 0, payload, IV_BYTES, ciphertext.length);
			return Base64.getEncoder().encodeToString(payload);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("Falha ao criptografar credenciais de canal", e);
		}
	}

	String decrypt(String encoded) {
		try {
			byte[] payload = Base64.getDecoder().decode(encoded);
			byte[] iv = Arrays.copyOfRange(payload, 0, IV_BYTES);
			byte[] ciphertext = Arrays.copyOfRange(payload, IV_BYTES, payload.length);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
			return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new IllegalStateException("Falha ao descriptografar credenciais de canal — chave errada ou dado corrompido", e);
		}
	}
}
