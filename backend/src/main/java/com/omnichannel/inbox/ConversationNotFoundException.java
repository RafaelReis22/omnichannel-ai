package com.omnichannel.inbox;

import java.util.UUID;

public class ConversationNotFoundException extends RuntimeException {

	public ConversationNotFoundException(UUID id) {
		super("Conversa ou contato não encontrado: " + id);
	}
}
