/**
 * Módulo de tenant: empresas (multi-tenant), usuários, papéis, autenticação,
 * permissões e auditoria administrativa.
 *
 * <p>Todo isolamento de dados por {@code tenant_id} + RLS nasce aqui
 * (docs/06-seguranca.md).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Tenant & Identidade")
package com.omnichannel.tenant;
