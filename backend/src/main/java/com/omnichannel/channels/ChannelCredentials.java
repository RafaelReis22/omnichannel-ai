package com.omnichannel.channels;

import java.util.Map;

/**
 * Credenciais de canal em claro, existindo apenas em memória durante o
 * uso — em repouso ficam criptografadas (AES-256-GCM) na tabela channels.
 */
public record ChannelCredentials(Map<String, String> values) {

	public ChannelCredentials {
		values = Map.copyOf(values);
	}

	public String require(String key) {
		String value = values.get(key);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Credencial obrigatória ausente: " + key);
		}
		return value;
	}
}
