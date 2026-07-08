package com.omnichannel.inbox.domain;

/**
 * OPEN: sem dono. WITH_AI: IA respondendo (Fase 1, módulo ai).
 * WITH_HUMAN: atribuída a um agente. RESOLVED: encerrada — reabre se o
 * contato mandar mensagem nova.
 */
public enum ConversationStatus {
	OPEN, WITH_AI, WITH_HUMAN, RESOLVED
}
