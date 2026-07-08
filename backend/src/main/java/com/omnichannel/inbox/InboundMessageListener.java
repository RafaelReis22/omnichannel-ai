package com.omnichannel.inbox;

import com.omnichannel.channels.InboundMessageReceived;
import com.omnichannel.shared.tenancy.TenantContext;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Consome os eventos do Channel Gateway (via outbox do Modulith — falha
 * aqui é reentregue, nunca perdida) e materializa no inbox.
 *
 * <p><b>RLS:</b> o listener roda assíncrono, fora de qualquer requisição —
 * a transação externa do {@code @ApplicationModuleListener} abre SEM
 * contexto de tenant. Por isso o processamento real acontece numa
 * transação REQUIRES_NEW aberta DEPOIS de assumir o contexto do evento;
 * na externa nada de banco é tocado.
 */
@Component
class InboundMessageListener {

	private final InboxService inboxService;
	private final TransactionTemplate tenantScopedTransaction;

	InboundMessageListener(InboxService inboxService, PlatformTransactionManager transactionManager) {
		this.tenantScopedTransaction = new TransactionTemplate(transactionManager);
		this.tenantScopedTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.inboxService = inboxService;
	}

	@ApplicationModuleListener
	void on(InboundMessageReceived event) {
		TenantContext.setTenantId(event.tenantId());
		try {
			tenantScopedTransaction.executeWithoutResult(status -> inboxService.registerInbound(event));
		}
		catch (DataIntegrityViolationException raceWithConcurrentListener) {
			// duas mensagens do mesmo contato novo processadas em paralelo: o
			// perdedor viola a unique de identidade. registerInbound é
			// idempotente — na segunda tentativa o contato já existe.
			tenantScopedTransaction.executeWithoutResult(status -> inboxService.registerInbound(event));
		}
		finally {
			TenantContext.clear();
		}
	}
}
