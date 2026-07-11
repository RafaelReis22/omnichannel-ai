package com.omnichannel.channels.email;

import java.net.URI;
import java.util.Properties;

import com.omnichannel.channels.ChannelAdapter;
import com.omnichannel.channels.ChannelCredentials;
import com.omnichannel.channels.ChannelDeliveryException;
import com.omnichannel.channels.ChannelType;
import com.omnichannel.channels.MessageContent;
import com.omnichannel.channels.MessageKind;
import com.omnichannel.channels.OutboundMessage;

import jakarta.activation.DataHandler;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Envio pelo canal de e-mail via SMTP, com credenciais do próprio canal
 * (cada tenant usa sua conta). Credenciais esperadas: smtp_host, smtp_port,
 * smtp_username, smtp_password, from_address.
 *
 * <p>Mídia vira anexo de verdade: URL pré-assinada expira em minutos, então
 * um link no corpo seria inútil para o destinatário — os bytes são baixados
 * aqui e embutidos na mensagem.
 */
@Component
class EmailChannelAdapter implements ChannelAdapter {

	private static final String SUBJECT = "Atendimento";

	private final RestClient restClient = RestClient.create();

	@Override
	public ChannelType type() {
		return ChannelType.EMAIL;
	}

	@Override
	public void send(OutboundMessage message, ChannelCredentials credentials) {
		MessageContent content = message.content();
		try {
			Session session = smtpSession(credentials);
			MimeMessage email = new MimeMessage(session);
			email.setFrom(new InternetAddress(credentials.require("from_address")));
			email.setRecipient(Message.RecipientType.TO, new InternetAddress(message.externalContactId()));
			email.setSubject(SUBJECT, "UTF-8");
			if (content.kind() == MessageKind.TEXT) {
				email.setText(content.text(), "UTF-8");
			}
			else {
				email.setContent(multipartWithAttachment(content));
			}
			Transport.send(email, credentials.require("smtp_username"),
					credentials.require("smtp_password"));
		}
		catch (Exception e) {
			throw new ChannelDeliveryException("Falha ao enviar e-mail: " + e.getMessage(), e);
		}
	}

	private MimeMultipart multipartWithAttachment(MessageContent content) throws jakarta.mail.MessagingException {
		// URI pronto, não String: o RestClient trataria a URL como template e
		// re-encodaria o % da assinatura pré-assinada, corrompendo-a
		byte[] bytes = restClient.get().uri(URI.create(content.mediaUrl())).retrieve().body(byte[].class);
		String mimeType = content.mimeType() == null ? "application/octet-stream" : content.mimeType();

		MimeBodyPart body = new MimeBodyPart();
		body.setText(content.text() == null ? "" : content.text(), "UTF-8");

		MimeBodyPart attachment = new MimeBodyPart();
		attachment.setDataHandler(new DataHandler(new ByteArrayDataSource(bytes, mimeType)));
		attachment.setFileName(attachmentName(content.mediaUrl()));

		MimeMultipart multipart = new MimeMultipart();
		multipart.addBodyPart(body);
		multipart.addBodyPart(attachment);
		return multipart;
	}

	/** Último segmento do caminho da chave (uuid-nomeoriginal), sem a query da assinatura. */
	private String attachmentName(String mediaUrl) {
		try {
			String path = URI.create(mediaUrl).getPath();
			String last = path.substring(path.lastIndexOf('/') + 1);
			// tira o prefixo uuid- gerado no upload, se presente
			return last.length() > 37 && last.charAt(36) == '-' ? last.substring(37) : last;
		}
		catch (Exception e) {
			return "anexo";
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
