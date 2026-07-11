package com.omnichannel.shared.storage;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Mídia em object storage. O RLS não alcança objetos, então a regra de
 * acesso é: mensagens guardam apenas a CHAVE ({@code storage://...}) e a
 * API resolve para URL pré-assinada de vida curta a cada leitura — quem
 * não enxerga a conversa (RLS) nunca recebe URL. Não existe endpoint de
 * acesso direto por chave.
 */
@Service
@EnableConfigurationProperties(StorageProperties.class)
public class StorageService {

	/** Prefixo que marca um media_url como chave interna, a presignar. */
	public static final String KEY_SCHEME = "storage://";

	private static final Duration PRESIGN_TTL = Duration.ofMinutes(15);

	private final StorageProperties properties;
	private final S3Client s3;
	private final S3Presigner presigner;

	public StorageService(StorageProperties properties) {
		this.properties = properties;
		var credentials = StaticCredentialsProvider
			.create(AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey()));
		// path-style: exigido pelo MinIO; inofensivo no R2
		var s3Config = S3Configuration.builder().pathStyleAccessEnabled(true).build();
		var region = Region.of(properties.region() == null ? "auto" : properties.region());
		// client HTTP do JDK: o apache5-client fica fora do classpath de
		// propósito (ver comentário no pom.xml)
		this.s3 = S3Client.builder()
			.httpClient(software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient.create())
			.endpointOverride(URI.create(properties.endpoint()))
			.credentialsProvider(credentials)
			.region(region)
			.serviceConfiguration(s3Config)
			.build();
		this.presigner = S3Presigner.builder()
			.endpointOverride(URI.create(properties.endpoint()))
			.credentialsProvider(credentials)
			.region(region)
			.serviceConfiguration(s3Config)
			.build();
		ensureBucket();
	}

	/** Grava a mídia e devolve a referência interna ({@code storage://...}). */
	public String store(UUID tenantId, String filename, String contentType, byte[] bytes) {
		String safeName = filename == null ? "arquivo"
				: filename.replaceAll("[^a-zA-Z0-9._-]", "_").substring(0, Math.min(filename.length(), 80));
		String key = "tenant/%s/%s-%s".formatted(tenantId, UUID.randomUUID(), safeName);
		s3.putObject(PutObjectRequest.builder()
			.bucket(properties.bucket())
			.key(key)
			.contentType(contentType)
			.build(), RequestBody.fromBytes(bytes));
		return KEY_SCHEME + key;
	}

	/** Resolve referência interna em URL pré-assinada; URLs externas passam intactas. */
	public String resolveUrl(String mediaUrl) {
		if (mediaUrl == null || !mediaUrl.startsWith(KEY_SCHEME)) {
			return mediaUrl;
		}
		String key = mediaUrl.substring(KEY_SCHEME.length());
		return presigner.presignGetObject(GetObjectPresignRequest.builder()
			.signatureDuration(PRESIGN_TTL)
			.getObjectRequest(GetObjectRequest.builder().bucket(properties.bucket()).key(key).build())
			.build()).url().toString();
	}

	private void ensureBucket() {
		try {
			s3.headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build());
		}
		catch (NoSuchBucketException e) {
			s3.createBucket(CreateBucketRequest.builder().bucket(properties.bucket()).build());
		}
	}
}
