package com.omnichannel.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM para segredos em repouso (credenciais de canal, segredos
 * TOTP — docs/06-seguranca.md). Formato: base64(iv de 12 bytes ||
 * ciphertext+tag). Cada finalidade usa sua própria chave (separação de
 * chaves); rotação exige re-criptografar os dados da finalidade.
 */
public final class AesGcmCipher {

	private static final int KEY_BYTES = 32;
	private static final int IV_BYTES = 12;
	private static final int GCM_TAG_BITS = 128;

	private final SecretKeySpec key;
	private final SecureRandom secureRandom = new SecureRandom();

	/**
	 * @param base64Key chave em base64 de exatamente 32 bytes
	 * @param keyName   nome da variável de ambiente, usado na mensagem de erro
	 */
	public AesGcmCipher(String base64Key, String keyName) {
		byte[] keyBytes;
		try {
			keyBytes = Base64.getDecoder().decode(base64Key == null ? "" : base64Key);
		}
		catch (IllegalArgumentException e) {
			keyBytes = new byte[0];
		}
		if (keyBytes.length != KEY_BYTES) {
			throw new IllegalStateException(
					"%s ausente ou inválida (esperado: base64 de %d bytes). Gere com: openssl rand -base64 %d"
						.formatted(keyName, KEY_BYTES, KEY_BYTES));
		}
		this.key = new SecretKeySpec(keyBytes, "AES");
	}

	public String encrypt(String plaintext) {
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
			throw new IllegalStateException("Falha ao criptografar segredo", e);
		}
	}

	public String decrypt(String encoded) {
		try {
			byte[] payload = Base64.getDecoder().decode(encoded);
			byte[] iv = Arrays.copyOfRange(payload, 0, IV_BYTES);
			byte[] ciphertext = Arrays.copyOfRange(payload, IV_BYTES, payload.length);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
			return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new IllegalStateException("Falha ao descriptografar segredo — chave errada ou dado corrompido", e);
		}
	}
}
