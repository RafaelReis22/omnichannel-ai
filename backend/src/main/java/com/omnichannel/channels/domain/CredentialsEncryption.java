package com.omnichannel.channels.domain;

import com.omnichannel.shared.security.AesGcmCipher;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Criptografia de credenciais de canal em repouso, com chave própria
 * (CHANNEL_CREDENTIALS_KEY). Implementação em
 * {@link AesGcmCipher} — compartilhada com o 2FA, chaves separadas.
 */
@Component
class CredentialsEncryption {

	private final AesGcmCipher cipher;

	CredentialsEncryption(@Value("${app.channels.credentials-key:}") String base64Key) {
		this.cipher = new AesGcmCipher(base64Key, "CHANNEL_CREDENTIALS_KEY");
	}

	String encrypt(String plaintext) {
		return cipher.encrypt(plaintext);
	}

	String decrypt(String encoded) {
		return cipher.decrypt(encoded);
	}
}
