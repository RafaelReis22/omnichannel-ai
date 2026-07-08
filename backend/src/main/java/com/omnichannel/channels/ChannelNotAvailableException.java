package com.omnichannel.channels;

/** Canal inexistente, desabilitado ou de tipo incompatível com a operação. */
public class ChannelNotAvailableException extends RuntimeException {

	public ChannelNotAvailableException(String message) {
		super(message);
	}
}
