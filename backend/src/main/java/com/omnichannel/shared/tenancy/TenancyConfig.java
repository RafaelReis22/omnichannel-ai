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
