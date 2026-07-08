package com.omnichannel.shared.ratelimit;

/** Limite excedido — mapeada para 429 com Retry-After. */
public class RateLimitExceededException extends RuntimeException {

	private final long retryAfterSeconds;

	public RateLimitExceededException(String message, long retryAfterSeconds) {
		super(message);
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}
}
