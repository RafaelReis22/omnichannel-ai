package com.omnichannel.shared.tenancy;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Contexto de tenant da requisição corrente, propagado até o banco via
 * {@link TenantAwareDataSource} (RLS — docs/06-seguranca.md).
 *
 * <p>Dois modos, mutuamente independentes:
 * <ul>
 *   <li><b>tenant</b>: definido pelo filtro de autenticação a partir do JWT;
 *       toda query enxerga apenas linhas do tenant.</li>
 *   <li><b>auth</b>: usado exclusivamente pelo fluxo de autenticação
 *       (login/signup/refresh), que precisa consultar por e-mail antes de
 *       haver tenant na sessão.</li>
 * </ul>
 */
public final class TenantContext {

	private static final ThreadLocal<UUID> TENANT_ID = new ThreadLocal<>();
	private static final ThreadLocal<Boolean> AUTH_CONTEXT = ThreadLocal.withInitial(() -> false);

	private TenantContext() {
	}

	public static void setTenantId(UUID tenantId) {
		TENANT_ID.set(tenantId);
	}

	public static UUID getTenantId() {
		return TENANT_ID.get();
	}

	public static boolean isAuthContext() {
		return AUTH_CONTEXT.get();
	}

	/** Há algum contexto que precise ser propagado à conexão? */
	public static boolean hasContext() {
		return TENANT_ID.get() != null || AUTH_CONTEXT.get();
	}

	/**
	 * Executa {@code action} em modo de autenticação. Deve envolver a
	 * transação inteira (a conexão é preparada quando a transação abre).
	 */
	public static <T> T runAsAuthProcess(Supplier<T> action) {
		AUTH_CONTEXT.set(true);
		try {
			return action.get();
		}
		finally {
			AUTH_CONTEXT.remove();
		}
	}

	public static void clear() {
		TENANT_ID.remove();
		AUTH_CONTEXT.remove();
	}
}
