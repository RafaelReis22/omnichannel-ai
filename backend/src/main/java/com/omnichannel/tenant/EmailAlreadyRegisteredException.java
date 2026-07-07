package com.omnichannel.tenant;

/** E-mail já cadastrado em qualquer tenant — mapeada para 409. */
public class EmailAlreadyRegisteredException extends RuntimeException {

	public EmailAlreadyRegisteredException() {
		super("E-mail já cadastrado");
	}
}
