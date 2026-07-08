package com.omnichannel.channels;

import java.util.UUID;

import com.omnichannel.channels.domain.ChannelService;
import com.omnichannel.shared.tenancy.TenantContext;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Porta de entrada única de mensagens: valida que o canal existe e está
 * ativo e publica {@link InboundMessageReceived} pelo outbox do Modulith.
 *
 * <p><b>Contrato de segurança:</b> quem chama (o webhook de cada adapter)
 * é responsável por autenticar o provedor (verificação de assinatura —
 * docs/06-seguranca.md) e resolver canal/tenant ANTES de chegar aqui.
 * Webhooks não têm JWT; o contexto de tenant é assumido a partir do canal
 * já autenticado, pela duração da transação.
 */
@Component
public class InboundMessageGateway {

	private final ChannelService channelService;
	private final ApplicationEventPublisher eventPublisher;
	private final TransactionTemplate transactionTemplate;

	public InboundMessageGateway(ChannelService channelService, ApplicationEventPublisher eventPublisher,
			PlatformTransactionManager transactionManager) {
		this.channelService = channelService;
		this.eventPublisher = eventPublisher;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public void receive(InboundMessageReceived message) {
		UUID previousTenant = TenantContext.getTenantId();
		TenantContext.setTenantId(message.tenantId());
		try {
			transactionTemplate.executeWithoutResult(status -> {
				channelService.requireActiveChannel(message.channelId(), message.channelType());
				eventPublisher.publishEvent(message);
			});
		}
		finally {
			if (previousTenant != null) {
				TenantContext.setTenantId(previousTenant);
			}
			else {
				TenantContext.clear();
			}
		}
	}
}
