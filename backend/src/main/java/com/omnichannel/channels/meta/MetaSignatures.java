package com.omnichannel.channels.meta;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Assinatura X-Hub-Signature-256 da Meta (HMAC-SHA256 do corpo bruto). */
public final class MetaSignatures {

	private MetaSignatures() {
	}

	public static String hmacSha256(String secret, String body) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception e) {
			throw new IllegalStateException("HMAC-SHA256 indisponível na JVM", e);
		}
	}
}
