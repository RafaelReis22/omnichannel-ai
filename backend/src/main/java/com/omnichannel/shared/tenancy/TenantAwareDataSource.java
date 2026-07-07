package com.omnichannel.shared.tenancy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Propaga o {@link TenantContext} para o Postgres na retirada da conexão
 * do pool, e limpa tudo na devolução (ADR-0002).
 *
 * <p>Com contexto presente: {@code SET ROLE app_runtime} (papel sem
 * BYPASSRLS — superusuários ignoram RLS) + GUCs {@code app.tenant_id} /
 * {@code app.auth_context} que as políticas de RLS leem. Sem contexto
 * (ex.: migrations Flyway no startup), a conexão passa intocada.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

	private static final String ASSUME_RUNTIME_ROLE = "SET ROLE app_runtime";

	private static final String RESET_SESSION = "RESET app.tenant_id; RESET app.auth_context; RESET ROLE";

	public TenantAwareDataSource(DataSource target) {
		super(target);
	}

	@Override
	public Connection getConnection() throws SQLException {
		return prepare(super.getConnection());
	}

	@Override
	public Connection getConnection(String username, String password) throws SQLException {
		return prepare(super.getConnection(username, password));
	}

	private Connection prepare(Connection target) throws SQLException {
		if (!TenantContext.hasContext()) {
			return target;
		}
		try {
			applyContext(target);
		}
		catch (SQLException e) {
			target.close();
			throw e;
		}
		return wrapForReset(target);
	}

	private void applyContext(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute(ASSUME_RUNTIME_ROLE);
		}
		UUID tenantId = TenantContext.getTenantId();
		if (tenantId != null) {
			setConfig(connection, "app.tenant_id", tenantId.toString());
		}
		if (TenantContext.isAuthContext()) {
			setConfig(connection, "app.auth_context", "on");
		}
	}

	private void setConfig(Connection connection, String key, String value) throws SQLException {
		// set_config com PreparedStatement: o valor nunca entra por concatenação
		try (PreparedStatement ps = connection.prepareStatement("SELECT set_config(?, ?, false)")) {
			ps.setString(1, key);
			ps.setString(2, value);
			ps.execute();
		}
	}

	/**
	 * Devolve um proxy que, no {@code close()}, restaura papel e GUCs antes
	 * de devolver a conexão ao pool — sem isso, o contexto vazaria para a
	 * próxima transação que reutilizar a mesma conexão física.
	 */
	private Connection wrapForReset(Connection target) {
		return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Connection.class },
				(proxy, method, args) -> {
					if ("close".equals(method.getName())) {
						resetQuietly(target);
					}
					try {
						return method.invoke(target, args);
					}
					catch (InvocationTargetException e) {
						throw e.getCause();
					}
				});
	}

	private void resetQuietly(Connection target) {
		try (Statement statement = target.createStatement()) {
			statement.execute(RESET_SESSION);
		}
		catch (SQLException ignored) {
			// conexão quebrada: o pool vai descartá-la, não há o que restaurar
		}
	}
}
