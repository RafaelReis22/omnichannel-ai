/**
 * Domínio do inbox, exposto como named interface: o módulo de IA lê
 * conversas e mensagens pelos tipos de domínio. Trocar por DTOs dedicados
 * quando a superfície entre módulos estabilizar (anotado no backlog).
 */
@org.springframework.modulith.NamedInterface("domain")
package com.omnichannel.inbox.domain;
