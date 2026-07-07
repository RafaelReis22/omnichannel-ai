/**
 * Módulo de canais (Channel Gateway): adapters de WhatsApp, webchat, e-mail,
 * Instagram, Messenger e Telegram, normalizando tudo para eventos internos.
 *
 * <p>Novo canal = novo adapter implementando o contrato comum — nenhum outro
 * módulo conhece detalhes de provedor (docs/03-arquitetura.md).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Channel Gateway")
package com.omnichannel.channels;
