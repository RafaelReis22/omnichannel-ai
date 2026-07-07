/**
 * Módulo compartilhado: tipos e contratos usados por todos os demais módulos
 * (eventos de domínio, identificadores, exceções base).
 *
 * <p>Único módulo que os outros podem referenciar livremente. Não contém
 * lógica de negócio — apenas contratos estáveis.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Shared Kernel")
package com.omnichannel.shared;
