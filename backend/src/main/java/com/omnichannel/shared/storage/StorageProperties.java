package com.omnichannel.shared.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuração do object storage ({@code app.storage.*} ← variáveis S3_*). */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String endpoint, String accessKeyId, String secretAccessKey, String bucket,
		String region) {

	public StorageProperties {
		if (isBlank(endpoint) || isBlank(accessKeyId) || isBlank(secretAccessKey) || isBlank(bucket)) {
			throw new IllegalStateException(
					"Object storage não configurado: defina S3_ENDPOINT, S3_ACCESS_KEY_ID, "
							+ "S3_SECRET_ACCESS_KEY e S3_BUCKET (MinIO local: docker compose up)");
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
