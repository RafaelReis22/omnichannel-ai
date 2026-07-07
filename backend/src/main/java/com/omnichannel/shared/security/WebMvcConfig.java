package com.omnichannel.shared.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class WebMvcConfig implements WebMvcConfigurer {

	private final TenantContextInterceptor tenantContextInterceptor;

	WebMvcConfig(TenantContextInterceptor tenantContextInterceptor) {
		this.tenantContextInterceptor = tenantContextInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(tenantContextInterceptor);
	}
}
