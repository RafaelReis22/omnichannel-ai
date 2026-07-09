package com.omnichannel.inbox;

import java.util.List;
import java.util.UUID;

import com.omnichannel.channels.InboundMessageReceived;
import com.omnichannel.channels.MessageContent;
import com.omnichannel.channels.OutboundMessage;
import com.omnichannel.channels.OutboundMessenger;
import com.omnichannel.inbox.domain.Contact;
import com.omnichannel.inbox.domain.ContactIdentity;
import com.omnichannel.inbox.domain.ContactIdentityRepository;
import com.omnichannel.inbox.domain.ContactRepository;
import com.omnichannel.inbox.domain.Conversation;
import com.omnichannel.inbox.domain.ConversationRepository;
import com.omnichannel.inbox.domain.ConversationStatus;
import com.omnichannel.inbox.domain.Message;
import com.omnichannel.inbox.domain.MessageRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Núcleo do inbox: materializa mensagens de entrada em
 * contato→conversa→mensagem e executa as ações de agente (atribuir,
 * resolver, etiquetar, responder). Todas as queries são escopadas pelo
 * RLS do tenant corrente.
 */
@Service
public class InboxService {

	private final ContactRepository contactRepository;
	private final ContactIdentityRepository contactIdentityRepository;
	private final ConversationRepository conversationRepository;
	private final MessageRepository messageRepository;
	private final com.omnichannel.inbox.domain.ConversationNoteRepository conversationNoteRepository;
	private final OutboundMessenger outboundMessenger;
	private final org.springframework.context.ApplicationEventPublisher eventPublisher;

	public InboxService(ContactRepository contactRepository, ContactIdentityRepository contactIdentityRepository,
			ConversationRepository conversationRepository, MessageRepository messageRepository,
			com.omnichannel.inbox.domain.ConversationNoteRepository conversationNoteRepository,
			OutboundMessenger outboundMessenger,
			org.springframework.context.ApplicationEventPublisher eventPublisher) {
		this.contactRepository = contactRepository;
		this.contactIdentityRepository = contactIdentityRepository;
		this.conversationRepository = conversationRepository;
		this.messageRepository = messageRepository;
		this.conversationNoteRepository = conversationNoteRepository;
		this.outboundMessenger = outboundMessenger;
		this.eventPublisher = eventPublisher;
	}

	/**
	 * Materializa um evento de mensagem recebida. Idempotente: reentrega de
	 * webhook com o mesmo external_message_id não duplica nada.
	 */
	@Transactional
	public void registerInbound(InboundMessageReceived event) {
		if (event.externalMessageId() != null
				&& messageRepository.existsByExternalMessageId(event.externalMessageId())) {
			return;
		}
		Contact contact = findOrCreateContact(event);
		Conversation conversation = findOrCreateConversation(event, contact);
		Message message = messageRepository.save(Message.fromContact(event.tenantId(), conversation.getId(),
				event.content(), event.externalMessageId()));
		conversation.registerActivity(message.previewText(), event.receivedAt());
		// gatilho do AI Worker — pelo outbox, na mesma transação
		eventPublisher.publishEvent(new ContactMessageRegistered(event.tenantId(), conversation.getId(),
				message.getId(), message.getText()));
		eventPublisher.publishEvent(new ConversationActivity(event.tenantId(), conversation.getId()));
	}

	/**
	 * Resposta autônoma da IA: persiste com confiança, marca a conversa como
	 * WITH_AI e envia pelo canal de origem. Chamado apenas pelo AI Worker.
	 */
	@Transactional
	public Message recordAiReply(UUID conversationId, MessageContent content, java.math.BigDecimal confidence) {
		Conversation conversation = getConversation(conversationId);
		ContactIdentity identity = identityFor(conversation);
		Message message = messageRepository
			.save(Message.fromAi(conversation.getTenantId(), conversation.getId(), content, confidence));
		conversation.registerActivity(message.previewText(), java.time.Instant.now());
		conversation.moveToAi();
		outboundMessenger.send(new OutboundMessage(conversation.getChannelId(), identity.getExternalId(), content));
		eventPublisher.publishEvent(new ConversationActivity(conversation.getTenantId(), conversation.getId()));
		return message;
	}

	/** Handoff: devolve a conversa à fila humana (status OPEN, sem dono). */
	@Transactional
	public void markNeedsHuman(UUID conversationId) {
		getConversation(conversationId).moveToHumanQueue();
	}

	@Transactional(readOnly = true)
	public Page<Conversation> listConversations(ConversationStatus status, UUID assigneeUserId, Pageable pageable) {
		return conversationRepository.search(status, assigneeUserId, pageable);
	}

	@Transactional(readOnly = true)
	public Conversation getConversation(UUID conversationId) {
		return conversationRepository.findById(conversationId)
			.orElseThrow(() -> new ConversationNotFoundException(conversationId));
	}

	@Transactional(readOnly = true)
	public Page<Message> messagesOf(UUID conversationId, Pageable pageable) {
		return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId, pageable);
	}

	/** Últimas mensagens em ordem cronológica — o contexto que a IA lê. */
	@Transactional(readOnly = true)
	public List<Message> recentMessages(UUID conversationId) {
		List<Message> newestFirst = messageRepository.findTop10ByConversationIdOrderByCreatedAtDesc(conversationId);
		return newestFirst.reversed();
	}

	@Transactional
	public Conversation assign(UUID conversationId, UUID userId) {
		Conversation conversation = getConversation(conversationId);
		conversation.assignTo(userId);
		eventPublisher.publishEvent(new ConversationActivity(conversation.getTenantId(), conversationId));
		return conversation;
	}

	@Transactional
	public Conversation resolve(UUID conversationId) {
		Conversation conversation = getConversation(conversationId);
		conversation.resolve();
		eventPublisher.publishEvent(new ConversationActivity(conversation.getTenantId(), conversationId));
		return conversation;
	}

	@Transactional
	public Conversation replaceTags(UUID conversationId, List<String> tags) {
		Conversation conversation = getConversation(conversationId);
		conversation.replaceTags(tags);
		return conversation;
	}

	/** Resposta de agente: persiste e envia pelo canal de origem. */
	@Transactional
	public Message replyAsAgent(UUID conversationId, UUID agentUserId, MessageContent content) {
		Conversation conversation = getConversation(conversationId);
		ContactIdentity identity = identityFor(conversation);
		Message message = messageRepository
			.save(Message.fromAgent(conversation.getTenantId(), conversation.getId(), content, agentUserId));
		// @CreationTimestamp só preenche createdAt no flush — usar o relógio
		// aqui, senão lastMessageAt ficaria nulo e quebraria a ordenação
		conversation.registerActivity(message.previewText(), java.time.Instant.now());
		outboundMessenger
			.send(new OutboundMessage(conversation.getChannelId(), identity.getExternalId(), content));
		eventPublisher.publishEvent(new ConversationActivity(conversation.getTenantId(), conversation.getId()));
		return message;
	}

	/**
	 * Thread do webchat vista pelo visitante (identificado por canal +
	 * visitorId): mensagens após {@code after}, em ordem cronológica.
	 * Roda sob o contexto de tenant já resolvido pelo ChannelDirectory.
	 */
	@Transactional(readOnly = true)
	public List<Message> webchatThread(UUID channelId, String visitorId, java.time.Instant after) {
		return contactIdentityRepository
			.findByChannelTypeAndExternalId(com.omnichannel.channels.ChannelType.WEBCHAT, visitorId)
			.flatMap(identity -> conversationRepository
				.findFirstByContactIdAndChannelIdOrderByLastMessageAtDesc(identity.getContactId(), channelId))
			.map(conversation -> messageRepository
				.findTop50ByConversationIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(conversation.getId(), after))
			.orElse(List.of());
	}

	/** Nota interna: visível só para a equipe; notifica os dashboards. */
	@Transactional
	public com.omnichannel.inbox.domain.ConversationNote addNote(UUID conversationId, UUID authorUserId,
			String text) {
		Conversation conversation = getConversation(conversationId);
		var note = conversationNoteRepository.save(new com.omnichannel.inbox.domain.ConversationNote(
				conversation.getTenantId(), conversationId, authorUserId, text));
		eventPublisher.publishEvent(new ConversationActivity(conversation.getTenantId(), conversationId));
		return note;
	}

	@Transactional(readOnly = true)
	public List<com.omnichannel.inbox.domain.ConversationNote> notesOf(UUID conversationId) {
		getConversation(conversationId); // 404 antes de listar
		return conversationNoteRepository.findAllByConversationIdOrderByCreatedAtAsc(conversationId);
	}

	@Transactional(readOnly = true)
	public Contact getContact(UUID contactId) {
		return contactRepository.findById(contactId)
			.orElseThrow(() -> new ConversationNotFoundException(contactId));
	}

	@Transactional(readOnly = true)
	public List<ContactIdentity> identitiesOf(UUID contactId) {
		return contactIdentityRepository.findAllByContactId(contactId);
	}

	@Transactional(readOnly = true)
	public List<Conversation> conversationHistoryOf(UUID contactId) {
		return conversationRepository.findAllByContactIdOrderByLastMessageAtDesc(contactId);
	}

	private Contact findOrCreateContact(InboundMessageReceived event) {
		return contactIdentityRepository.findByChannelTypeAndExternalId(event.channelType(), event.externalContactId())
			.map(identity -> contactRepository.findById(identity.getContactId()).orElseThrow())
			.orElseGet(() -> {
				String name = event.externalContactName() != null ? event.externalContactName()
						: event.externalContactId();
				Contact contact = contactRepository.save(new Contact(event.tenantId(), name));
				contactIdentityRepository.save(new ContactIdentity(event.tenantId(), contact.getId(),
						event.channelType(), event.externalContactId()));
				return contact;
			});
	}

	private Conversation findOrCreateConversation(InboundMessageReceived event, Contact contact) {
		return conversationRepository
			.findFirstByContactIdAndChannelIdAndStatusNotOrderByCreatedAtDesc(contact.getId(), event.channelId(),
					ConversationStatus.RESOLVED)
			.or(() -> conversationRepository
				.findFirstByContactIdAndChannelIdOrderByLastMessageAtDesc(contact.getId(), event.channelId()))
			.orElseGet(() -> conversationRepository
				.save(new Conversation(event.tenantId(), contact.getId(), event.channelId())));
	}

	private ContactIdentity identityFor(Conversation conversation) {
		return contactIdentityRepository.findAllByContactId(conversation.getContactId())
			.stream()
			.findFirst()
			.orElseThrow(() -> new IllegalStateException(
					"Contato %s sem identidade de canal".formatted(conversation.getContactId())));
	}
}
