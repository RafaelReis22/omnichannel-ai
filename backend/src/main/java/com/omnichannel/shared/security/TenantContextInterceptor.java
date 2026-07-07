package com.omnichannel.shared.security;

import java.util.UUID;

import com.omnichannel.shared.tenancy.TenantContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Extrai o {@code tenant_id} do JWT autenticado e o coloca no
 * {@link TenantContext} pela duração da requisição — é daqui que o RLS
 * recebe o tenant de cada query. Limpeza garantida no afterCompletion,
 * inclusive em exceção.
 */
@Component
public class TenantContextInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
			String tenantId = jwtAuthentication.getToken().getClaimAsString(JwtService.CLAIM_TENANT_ID);
			if (tenantId != null) {
				TenantContext.setTenantId(UUID.fromString(tenantId));
			}
		}
		return true;
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception exception) {
		TenantContext.clear();
	}
}
