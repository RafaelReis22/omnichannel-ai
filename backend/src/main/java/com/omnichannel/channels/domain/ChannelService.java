package com.omnichannel.channels.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnichannel.channels.ChannelCredentials;
import com.omnichannel.channels.ChannelNotAvailableException;
import com.omnichannel.channels.ChannelType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestão de canais do tenant corrente (o RLS escopa todas as queries).
 * Credenciais entram como mapa, viram JSON e são criptografadas antes de
 * tocar o banco; só voltam ao claro para uso de um adapter.
 */
@Service
public class ChannelService {

	private final ChannelRepository channelRepository;
	private final CredentialsEncryption credentialsEncryption;
	private final ObjectMapper objectMapper;

	public ChannelService(ChannelRepository channelRepository, CredentialsEncryption credentialsEncryption,
			ObjectMapper objectMapper) {
		this.channelRepository = channelRepository;
		this.credentialsEncryption = credentialsEncryption;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public Channel connect(UUID tenantId, ChannelType type, String name, Map<String, String> credentials) {
		String encrypted = credentialsEncryption.encrypt(toJson(credentials));
		return channelRepository.save(new Channel(tenantId, type, name, encrypted));
	}

	@Transactional(readOnly = true)
	public List<Channel> list() {
		return channelRepository.findAll();
	}

	@Transactional
	public Channel disable(UUID channelId) {
		Channel channel = channelRepository.findById(channelId)
			.orElseThrow(() -> new ChannelNotAvailableException("Canal não encontrado"));
		channel.disable();
		return channel;
	}

	@Transactional
	public Channel activate(UUID channelId) {
		Channel channel = channelRepository.findById(channelId)
			.orElseThrow(() -> new ChannelNotAvailableException("Canal não encontrado"));
		channel.activate();
		return channel;
	}

	/** Exclusão real — o banco recusa (FK) se o canal já tem conversas. */
	@Transactional
	public void delete(UUID channelId) {
		Channel channel = channelRepository.findById(channelId)
			.orElseThrow(() -> new ChannelNotAvailableException("Canal não encontrado"));
		channelRepository.delete(channel);
	}

	/** Tipos por id — enriquecimento da lista do inbox, no contexto do chamador. */
	@Transactional(readOnly = true)
	public java.util.Map<UUID, ChannelType> typesOf(java.util.Collection<UUID> channelIds) {
		return channelRepository.findAllById(channelIds)
			.stream()
			.collect(java.util.stream.Collectors.toMap(Channel::getId, Channel::getType));
	}

	/** Canais ativos de um tipo — usado pelo poller de e-mail sob contexto de sistema. */
	@Transactional(readOnly = true)
	public List<Channel> findActiveByType(ChannelType type) {
		return channelRepository.findAllByTypeAndStatus(type, ChannelStatus.ACTIVE);
	}

	@Transactional(readOnly = true)
	public java.util.Optional<Channel> findActivePublic(UUID channelId, ChannelType expectedType) {
		return channelRepository.findById(channelId)
			.filter(Channel::isActive)
			.filter(channel -> channel.getType() == expectedType);
	}

	@Transactional(readOnly = true)
	public java.util.Optional<Channel> findActiveOfAnyType(UUID channelId, java.util.Set<ChannelType> types) {
		return channelRepository.findById(channelId)
			.filter(Channel::isActive)
			.filter(channel -> types.contains(channel.getType()));
	}

	@Transactional(readOnly = true)
	public Channel requireActiveChannelForSend(UUID channelId) {
		return channelRepository.findById(channelId)
			.filter(Channel::isActive)
			.orElseThrow(() -> new ChannelNotAvailableException("Canal indisponível para envio: " + channelId));
	}

	@Transactional(readOnly = true)
	public void requireActiveChannel(UUID channelId, ChannelType expectedType) {
		Channel channel = channelRepository.findById(channelId)
			.filter(Channel::isActive)
			.orElseThrow(() -> new ChannelNotAvailableException("Canal indisponível: " + channelId));
		if (channel.getType() != expectedType) {
			throw new ChannelNotAvailableException(
					"Canal %s é %s, mensagem chegou como %s".formatted(channelId, channel.getType(), expectedType));
		}
	}

	@Transactional(readOnly = true)
	public ChannelCredentials credentialsOf(UUID channelId) {
		Channel channel = channelRepository.findById(channelId)
			.orElseThrow(() -> new ChannelNotAvailableException("Canal não encontrado"));
		return new ChannelCredentials(fromJson(credentialsEncryption.decrypt(channel.getCredentialsEncrypted())));
	}

	private String toJson(Map<String, String> credentials) {
		try {
			return objectMapper.writeValueAsString(credentials);
		}
		catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Credenciais não serializáveis", e);
		}
	}

	private Map<String, String> fromJson(String json) {
		try {
			return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
			});
		}
		catch (JsonProcessingException e) {
			throw new IllegalStateException("Credenciais armazenadas corrompidas", e);
		}
	}
}
