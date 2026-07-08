package com.omnichannel.shared.ratelimit;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Contador de janela fixa em Redis (INCR + TTL). Suficiente contra força
 * bruta em autenticação — limites baixos tornam irrelevante a fraqueza de
 * borda de janela do algoritmo; se um dia precisarmos de rate limiting de
 * API de alto volume (Fase 4), aí sim avaliar sliding window/token bucket.
 */
@Component
public class RateLimiter {

	private final StringRedisTemplate redis;

	public RateLimiter(StringRedisTemplate redis) {
		this.redis = redis;
	}

	/** Registra uma ocorrência e responde se ainda está dentro do limite. */
	public boolean tryAcquire(String key, int limit, Duration window) {
		return record(key, window) <= limit;
	}

	/** Registra uma ocorrência (ex.: falha de login) e devolve o total na janela. */
	public long record(String key, Duration window) {
		Long count = redis.opsForValue().increment(key);
		if (count != null && count == 1) {
			redis.expire(key, window);
		}
		else if (count != null && redis.getExpire(key) < 0) {
			// autocura: INCR criou/achou chave sem TTL (expire anterior falhou)
			// — sem isso o bloqueio nunca expiraria
			redis.expire(key, window);
		}
		return count == null ? 0 : count;
	}

	public boolean isBlocked(String key, int limit) {
		String value = redis.opsForValue().get(key);
		return value != null && Long.parseLong(value) >= limit;
	}

	public void reset(String key) {
		redis.delete(key);
	}

	/** Segundos até a janela expirar — alimenta o cabeçalho Retry-After. */
	public long secondsUntilReset(String key, Duration window) {
		Long ttl = redis.getExpire(key);
		return (ttl == null || ttl < 0) ? window.toSeconds() : ttl;
	}
}
