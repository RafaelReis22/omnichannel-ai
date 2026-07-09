package com.omnichannel.channels.email;

import java.util.Properties;

import com.omnichannel.channels.ChannelAdapter;
import com.omnichannel.channels.ChannelCredentials;
import com.omnichannel.channels.ChannelDeliveryException;
import com.omnichannel.channels.ChannelType;
import com.omnichannel.channels.MessageKind;
import com.omnichannel.channels.OutboundMessage;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.springframework.stereotype.Component;

/**
 * Envio pelo canal de e-mail via SMTP, com credenciais do próprio canal
 * (cada tenant usa sua conta). Credenciais esperadas: smtp_host, smtp_port,
 * smtp_username, smtp_password, from_address.
 */
@Component
class EmailChannelAdapter implements ChannelAdapter {

	private static final String SUBJECT = "Atendimento";

	@Override
	public ChannelType type() {
		return ChannelType.EMAIL;
	}

	@Override
	public void send(OutboundMessage message, ChannelCredentials credentials) {
		if (message.content().kind() != MessageKind.TEXT) {
			throw new ChannelDeliveryException("Canal de e-mail só envia texto no MVP");
		}
		try {
			Session session = smtpSession(credentials);
			MimeMessage email = new MimeMessage(session);
			email.setFrom(new InternetAddress(credentials.require("from_address")));
			email.setRecipient(Message.RecipientType.TO, new InternetAddress(message.externalContactId()));
			email.setSubject(SUBJECT, "UTF-8");
			email.setText(message.content().text(), "UTF-8");
			Transport.send(email, credentials.require("smtp_username"),
					credentials.require("smtp_password"));
		}
		catch (Exception e) {
			throw new ChannelDeliveryException("Falha ao enviar e-mail: " + e.getMessage(), e);
		}
	}

	private Session smtpSession(ChannelCredentials credentials) {
		Properties properties = new Properties();
		properties.put("mail.smtp.host", credentials.require("smtp_host"));
		properties.put("mail.smtp.port", credentials.require("smtp_port"));
		properties.put("mail.smtp.auth", "true");
		// STARTTLS em produção; testes (Greenmail) usam texto plano local
		properties.put("mail.smtp.starttls.enable",
				credentials.values().getOrDefault("smtp_starttls", "true"));
		return Session.getInstance(properties);
	}
}
