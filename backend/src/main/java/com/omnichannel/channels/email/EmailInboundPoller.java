package com.omnichannel.channels.email;

import java.time.Instant;
import java.util.List;
import java.util.Properties;

import com.omnichannel.channels.ChannelType;
import com.omnichannel.channels.InboundMessageGateway;
import com.omnichannel.channels.InboundMessageReceived;
import com.omnichannel.channels.MessageContent;
import com.omnichannel.channels.domain.Channel;
import com.omnichannel.channels.domain.ChannelService;
import com.omnichannel.shared.tenancy.TenantContext;

import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.FlagTerm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ingestão do canal de e-mail: varre a caixa IMAP de cada canal EMAIL
 * ativo (todos os tenants) buscando mensagens não lidas, normaliza e
 * entrega ao gateway — que valida, assume o contexto do tenant e publica
 * pelo outbox. Dedup fica com o inbox, via Message-ID.
 *
 * <p>Falha em um canal não derruba os demais: loga e segue. Credenciais
 * esperadas: imap_host, imap_port, imap_username, imap_password.
 */
@Component
class EmailInboundPoller {

	private static final Logger log = LoggerFactory.getLogger(EmailInboundPoller.class);

	private final ChannelService channelService;
	private final InboundMessageGateway gateway;

	EmailInboundPoller(ChannelService channelService, InboundMessageGateway gateway) {
		this.channelService = channelService;
		this.gateway = gateway;
	}

	@Scheduled(fixedDelayString = "${app.channels.email.poll-interval:PT30S}")
	void pollAll() {
		// listagem cross-tenant: contexto de sistema (SELECT apenas — V7)
		List<Channel> emailChannels = TenantContext
			.runAsAuthProcess(() -> channelService.findActiveByType(ChannelType.EMAIL));
		for (Channel channel : emailChannels) {
			try {
				pollChannel(channel);
			}
			catch (Exception e) {
				log.warn("Falha ao varrer e-mail do canal {}: {}", channel.getId(), e.getMessage());
			}
		}
	}

	private void pollChannel(Channel channel) throws Exception {
		TenantContext.setTenantId(channel.getTenantId());
		var credentials = channelService.credentialsOf(channel.getId());
		TenantContext.clear();

		Properties properties = new Properties();
		properties.put("mail.store.protocol", "imap");
		properties.put("mail.imap.host", credentials.require("imap_host"));
		properties.put("mail.imap.port", credentials.require("imap_port"));

		Session session = Session.getInstance(properties);
		try (Store store = session.getStore()) {
			store.connect(credentials.require("imap_username"), credentials.require("imap_password"));
			Folder inbox = store.getFolder("INBOX");
			inbox.open(Folder.READ_WRITE);
			try {
				Message[] unseen = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
				for (Message message : unseen) {
					deliver(channel, message);
					message.setFlag(Flags.Flag.SEEN, true);
				}
			}
			finally {
				inbox.close(false);
			}
		}
	}

	private void deliver(Channel channel, Message message) throws Exception {
		InternetAddress from = (InternetAddress) message.getFrom()[0];
		String messageId = message instanceof MimeMessage mime ? mime.getMessageID() : null;
		String subject = message.getSubject();
		String body = extractText(message);
		String text = (subject == null || subject.isBlank() ? "" : subject + "\n\n") + body;

		gateway.receive(new InboundMessageReceived(channel.getTenantId(), channel.getId(), ChannelType.EMAIL,
				from.getAddress(), from.getPersonal(), messageId, MessageContent.text(text.strip()),
				Instant.now()));
	}

	private String extractText(Message message) throws Exception {
		Object content = message.getContent();
		if (content instanceof String text) {
			return text;
		}
		if (content instanceof jakarta.mail.Multipart multipart) {
			for (int i = 0; i < multipart.getCount(); i++) {
				var part = multipart.getBodyPart(i);
				if (part.isMimeType("text/plain")) {
					return String.valueOf(part.getContent());
				}
			}
		}
		return "(mensagem sem texto)";
	}
}
