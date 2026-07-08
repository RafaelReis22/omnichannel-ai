package com.omnichannel.channels;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Resolve o {@link ChannelAdapter} de cada tipo de canal a partir dos
 * adapters registrados no contexto Spring — plugar um canal novo é criar
 * um bean que implementa o contrato, sem tocar aqui.
 */
@Component
public class ChannelRegistry {

	private final Map<ChannelType, ChannelAdapter> adapters = new EnumMap<>(ChannelType.class);

	public ChannelRegistry(List<ChannelAdapter> registeredAdapters) {
		for (ChannelAdapter adapter : registeredAdapters) {
			ChannelAdapter previous = adapters.put(adapter.type(), adapter);
			if (previous != null) {
				throw new IllegalStateException(
						"Dois adapters registrados para o canal %s: %s e %s".formatted(adapter.type(),
								previous.getClass().getSimpleName(), adapter.getClass().getSimpleName()));
			}
		}
	}

	public ChannelAdapter adapterFor(ChannelType type) {
		ChannelAdapter adapter = adapters.get(type);
		if (adapter == null) {
			throw new ChannelNotAvailableException("Nenhum adapter registrado para o canal " + type);
		}
		return adapter;
	}

	public boolean supports(ChannelType type) {
		return adapters.containsKey(type);
	}
}
