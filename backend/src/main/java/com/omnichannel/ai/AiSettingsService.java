package com.omnichannel.ai;

import java.math.BigDecimal;
import java.util.UUID;

import com.omnichannel.ai.domain.AiSettings;
import com.omnichannel.ai.domain.AiSettingsRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiSettingsService {

	private final AiSettingsRepository repository;

	public AiSettingsService(AiSettingsRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public AiSettings settingsOf(UUID tenantId) {
		return repository.findById(tenantId).orElseGet(() -> new AiSettings(tenantId));
	}

	@Transactional
	public AiSettings update(UUID tenantId, boolean enabled, BigDecimal confidenceThreshold, String tone) {
		return update(tenantId, enabled, confidenceThreshold, tone, null);
	}

	@Transactional
	public AiSettings update(UUID tenantId, boolean enabled, BigDecimal confidenceThreshold, String tone,
			String assistantName) {
		AiSettings settings = repository.findById(tenantId).orElseGet(() -> new AiSettings(tenantId));
		settings.update(enabled, confidenceThreshold, tone, assistantName);
		return repository.save(settings);
	}
}
