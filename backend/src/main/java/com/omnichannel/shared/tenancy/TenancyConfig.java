package com.omnichannel.shared.tenancy;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Envolve o DataSource auto-configurado (Hikari) no
 * {@link TenantAwareDataSource} sem substituir o bean — preserva a
 * auto-configuração do Boot e o {@code @ServiceConnection} dos testes.
 */
@Configuration(proxyBeanMethods = false)
class TenancyConfig {

	/**
	 * OSIV segura a conexão pela requisição inteira — a conexão preparada
	 * pelo primeiro contexto (ex.: auth) seria reutilizada pelos seguintes
	 * (ex.: tenant), quebrando silenciosamente o RLS (ADR-0002). A checagem
	 * usa a presença real do interceptor (verdade do contexto, imune a
	 * pegadinhas de property source) e falha o startup.
	 */
	@Bean
	static org.springframework.beans.factory.SmartInitializingSingleton osivGuard(
			org.springframework.beans.factory.ObjectProvider<org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor> osivInterceptor) {
		return () -> {
			if (osivInterceptor.getIfAvailable() != null) {
				throw new IllegalStateException(
						"spring.jpa.open-in-view deve ser false: OSIV reutiliza a conexão entre transações "
								+ "e quebra a propagação de contexto do RLS (ADR-0002)");
			}
		};
	}

	@Bean
	static BeanPostProcessor tenantAwareDataSourceWrapper() {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessAfterInitialization(Object bean, String beanName) {
				if (bean instanceof DataSource dataSource && !(bean instanceof TenantAwareDataSource)) {
					return new TenantAwareDataSource(dataSource);
				}
				return bean;
			}
		};
	}
}
