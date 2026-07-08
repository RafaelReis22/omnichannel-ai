package com.omnichannel.channels;

/** Falha de entrega em um canal — sinaliza retry/DLQ ao chamador. */
public class ChannelDeliveryException extends RuntimeException {

	public ChannelDeliveryException(String message, Throwable cause) {
		super(message, cause);
	}

	public ChannelDeliveryException(String message) {
		super(message);
	}
}
