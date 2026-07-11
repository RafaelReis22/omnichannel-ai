package com.omnichannel.channels.whatsapp;

import java.net.URI;
import java.util.Map;

import com.omnichannel.channels.ChannelCredentials;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Download de mídia recebida pelo WhatsApp, nos dois provedores:
 * <ul>
 *   <li><b>Cloud API</b>: {@code GET {base}/{media_id}} (Bearer) devolve a
 *       URL efêmera do CDN da Meta, baixada com o mesmo token;</li>
 *   <li><b>360dialog</b>: mesmo contrato, com {@code D360-API-KEY}; a URL
 *       devolvida aponta para o lookaside da Meta e precisa ser reescrita
 *       para o host do BSP (regra documentada pela 360dialog).</li>
 * </ul>
 */
@Component
class WhatsAppMediaClient {

	private final RestClient restClient = RestClient.create();

	record DownloadedMedia(byte[] bytes, String mimeType) {
	}

	DownloadedMedia download(String mediaId, ChannelCredentials credentials) {
		boolean cloudApi = credentials.values().containsKey("phone_number_id");
		String baseUrl = credentials.values().getOrDefault("base_url",
				cloudApi ? WhatsAppChannelAdapter.CLOUD_BASE_URL : WhatsAppChannelAdapter.DIALOG360_BASE_URL);

		Map<String, Object> metadata = authorized(restClient.get().uri(baseUrl + "/" + mediaId), credentials)
			.retrieve()
			.body(Map.class);
		String mediaUrl = (String) metadata.get("url");
		String mimeType = (String) metadata.getOrDefault("mime_type", "application/octet-stream");
		if (!cloudApi) {
			mediaUrl = rewriteToBase(mediaUrl, baseUrl);
		}
		// URI pronto, não String: como template, o RestClient re-encodaria os
		// % da query assinada do lookaside e quebraria o download
		byte[] bytes = authorized(restClient.get().uri(URI.create(mediaUrl)), credentials).retrieve()
			.body(byte[].class);
		return new DownloadedMedia(bytes, mimeType);
	}

	private RestClient.RequestHeadersSpec<?> authorized(RestClient.RequestHeadersSpec<?> spec,
			ChannelCredentials credentials) {
		if (credentials.values().containsKey("phone_number_id")) {
			return spec.header("Authorization", "Bearer " + credentials.require("access_token"));
		}
		return spec.header("D360-API-KEY", credentials.require("api_key"));
	}

	/** Troca scheme+host da URL do lookaside pelo host do BSP, mantendo caminho e query. */
	private String rewriteToBase(String url, String baseUrl) {
		URI original = URI.create(url);
		URI base = URI.create(baseUrl);
		String pathAndQuery = original.getRawPath()
				+ (original.getRawQuery() == null ? "" : "?" + original.getRawQuery());
		return base.getScheme() + "://" + base.getRawAuthority() + pathAndQuery;
	}
}
