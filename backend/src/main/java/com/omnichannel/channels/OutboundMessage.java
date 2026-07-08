package com.omnichannel.channels;

import java.util.UUID;

/** Mensagem de saída normalizada, entregue a um {@link ChannelAdapter}. */
public record OutboundMessage(UUID channelId, String externalContactId, MessageContent content) {
}
