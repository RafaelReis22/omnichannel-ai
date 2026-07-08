package com.omnichannel.channels;

import java.util.Optional;
import java.util.UUID;

import com.omnichannel.channels.domain.ChannelService;
import com.omnichannel.shared.tenancy.TenantContext;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Resolve canais públicos ANTES de existir contexto de tenant — o caso do
 * widget de webchat, onde o visitante só conhece o channelId (site key).
 * Usa o contexto de sistema (leitura apenas — migration V7). Expõe só o
 * mínimo: id, tenant e tipo; nunca credenciais.
 */
@Component
public class ChannelDirectory {

	public record PublicChannel(UUID channelId, UUID tenantId, ChannelType type) {
	}

	private final ChannelService channelService;
	private final TransactionTemplate transactionTemplate;

	public ChannelDirectory(ChannelService channelService, PlatformTransactionManager transactionManager) {
		this.channelService = channelService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.transactionTemplate.setReadOnly(true);
	}

	public Optional<PublicChannel> findActiveWebchat(UUID channelId) {
		return TenantContext.runAsAuthProcess(() -> transactionTemplate.execute(
				status -> channelService.findActivePublic(channelId, ChannelType.WEBCHAT)
					.map(channel -> new PublicChannel(channel.getId(), channel.getTenantId(), channel.getType()))));
	}
}
