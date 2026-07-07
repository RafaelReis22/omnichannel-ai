/**
 * API de tenancy exposta aos demais módulos: contexto de tenant da
 * requisição e datasource que o propaga ao RLS do Postgres (ADR-0002).
 */
@org.springframework.modulith.NamedInterface("tenancy")
package com.omnichannel.shared.tenancy;
