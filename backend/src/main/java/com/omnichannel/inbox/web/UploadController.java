package com.omnichannel.inbox.web;

import java.io.IOException;
import java.util.Set;

import com.omnichannel.shared.storage.StorageService;
import com.omnichannel.shared.tenancy.TenantContext;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Upload de mídia para anexar em respostas. Devolve a referência interna
 * ({@code storage://...}) que vai no {@code mediaUrl} da mensagem, e uma
 * URL de pré-visualização de vida curta.
 */
@RestController
class UploadController {

	private static final long MAX_BYTES = 15L * 1024 * 1024;
	private static final Set<String> ALLOWED_PREFIXES = Set.of("image/", "video/", "audio/");
	private static final Set<String> ALLOWED_EXACT = Set.of("application/pdf");

	private final StorageService storageService;

	UploadController(StorageService storageService) {
		this.storageService = storageService;
	}

	record UploadResponse(String mediaUrl, String previewUrl, String contentType, String kind) {
	}

	@PostMapping(value = "/api/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	UploadResponse upload(@RequestParam("file") MultipartFile file) throws IOException {
		if (file.getSize() > MAX_BYTES) {
			throw new UploadRejectedException("Arquivo acima de 15 MB");
		}
		String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
		boolean allowed = ALLOWED_EXACT.contains(contentType)
				|| ALLOWED_PREFIXES.stream().anyMatch(contentType::startsWith);
		if (!allowed) {
			throw new UploadRejectedException("Tipo não suportado: envie imagem, vídeo, áudio ou PDF");
		}
		String mediaUrl = storageService.store(TenantContext.getTenantId(), file.getOriginalFilename(),
				contentType, file.getBytes());
		return new UploadResponse(mediaUrl, storageService.resolveUrl(mediaUrl), contentType, kindOf(contentType));
	}

	private String kindOf(String contentType) {
		if (contentType.startsWith("image/")) {
			return "IMAGE";
		}
		if (contentType.startsWith("video/")) {
			return "VIDEO";
		}
		if (contentType.startsWith("audio/")) {
			return "AUDIO";
		}
		return "DOCUMENT";
	}

	static class UploadRejectedException extends RuntimeException {
		UploadRejectedException(String message) {
			super(message);
		}
	}

	@ExceptionHandler(UploadRejectedException.class)
	ProblemDetail handleRejected(UploadRejectedException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
	}
}
