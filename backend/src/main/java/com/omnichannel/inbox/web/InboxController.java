package com.omnichannel.inbox.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.omnichannel.channels.MessageContent;
import com.omnichannel.channels.MessageKind;
import com.omnichannel.inbox.ConversationNotFoundException;
import com.omnichannel.inbox.InboxService;
import com.omnichannel.inbox.domain.Contact;
import com.omnichannel.inbox.domain.Conversation;
import com.omnichannel.inbox.domain.ConversationStatus;
import com.omnichannel.inbox.domain.Message;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API do painel único de conversas (docs/02-plano-de-produto.md, MVP). */
@RestController
@RequestMapping("/api")
class InboxController {

	private static final int MAX_PAGE_SIZE = 100;

	private final InboxService inboxService;
	private final com.omnichannel.inbox.SearchService searchService;
	private final com.omnichannel.inbox.domain.ContactRepository contactRepository;
	private final com.omnichannel.channels.ChannelDirectory channelDirectory;
	private final com.omnichannel.shared.storage.StorageService storageService;

	InboxController(InboxService inboxService, com.omnichannel.inbox.SearchService searchService,
			com.omnichannel.inbox.domain.ContactRepository contactRepository,
			com.omnichannel.channels.ChannelDirectory channelDirectory,
			com.omnichannel.shared.storage.StorageService storageService) {
		this.inboxService = inboxService;
		this.searchService = searchService;
		this.contactRepository = contactRepository;
		this.channelDirectory = channelDirectory;
		this.storageService = storageService;
	}

	record ConversationResponse(UUID id, UUID contactId, String contactName, UUID channelId, String channelType,
			String status, UUID assigneeUserId, List<String> tags, String lastMessagePreview,
			Instant lastMessageAt, Instant createdAt) {
	}

	/** Nome do contato e tipo do canal na resposta: a lista do inbox é lida o dia inteiro. */
	private List<ConversationResponse> enrich(List<Conversation> conversations) {
		var contactNames = contactRepository
			.findAllById(conversations.stream().map(Conversation::getContactId).distinct().toList())
			.stream()
			.collect(java.util.stream.Collectors.toMap(Contact::getId, Contact::getName));
		var channelTypes = channelDirectory
			.typesOf(conversations.stream().map(Conversation::getChannelId).distinct().toList());
		return conversations.stream()
			.map(c -> new ConversationResponse(c.getId(), c.getContactId(),
					contactNames.getOrDefault(c.getContactId(), "?"), c.getChannelId(),
					channelTypes.containsKey(c.getChannelId()) ? channelTypes.get(c.getChannelId()).name() : null,
					c.getStatus().name(), c.getAssigneeUserId(), c.getTags(), c.getLastMessagePreview(),
					c.getLastMessageAt(), c.getCreatedAt()))
			.toList();
	}

	private ConversationResponse enrichOne(Conversation conversation) {
		return enrich(List.of(conversation)).getFirst();
	}

	record MessageResponse(UUID id, String sender, String kind, String text, String mediaUrl, UUID senderUserId,
			Instant createdAt) {
	}

	/** mediaUrl interno (storage://) vira URL pré-assinada de vida curta. */
	private MessageResponse toMessageResponse(Message m) {
		return new MessageResponse(m.getId(), m.getSender().name(), m.getKind().name(), m.getText(),
				storageService.resolveUrl(m.getMediaUrl()), m.getSenderUserId(), m.getCreatedAt());
	}

	record AssignRequest(@NotNull UUID userId) {
	}

	record TagsRequest(@NotNull List<String> tags) {
	}

	record ReplyRequest(@NotNull MessageKind kind, String text, String mediaUrl, String mimeType) {
		MessageContent toContent() {
			return new MessageContent(kind, text, mediaUrl, mimeType);
		}
	}

	@GetMapping("/conversations")
	Page<ConversationResponse> list(@RequestParam(required = false) ConversationStatus status,
			@RequestParam(required = false) UUID assigneeUserId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size) {
		var result = inboxService.listConversations(status, assigneeUserId,
				PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE)));
		List<ConversationResponse> enriched = enrich(result.getContent());
		return new org.springframework.data.domain.PageImpl<>(enriched, result.getPageable(),
				result.getTotalElements());
	}

	@GetMapping("/conversations/{id}")
	ConversationResponse get(@PathVariable UUID id) {
		return enrichOne(inboxService.getConversation(id));
	}

	@GetMapping("/conversations/{id}/messages")
	Page<MessageResponse> messages(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		inboxService.getConversation(id); // 404 antes de listar
		return inboxService.messagesOf(id, PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE)))
			.map(this::toMessageResponse);
	}

	@PostMapping("/conversations/{id}/assign")
	ConversationResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignRequest request) {
		return enrichOne(inboxService.assign(id, request.userId()));
	}

	@PostMapping("/conversations/{id}/resolve")
	ConversationResponse resolve(@PathVariable UUID id) {
		return enrichOne(inboxService.resolve(id));
	}

	@PutMapping("/conversations/{id}/tags")
	ConversationResponse tags(@PathVariable UUID id, @Valid @RequestBody TagsRequest request) {
		return enrichOne(inboxService.replaceTags(id, request.tags()));
	}

	@PostMapping("/conversations/{id}/reply")
	@ResponseStatus(HttpStatus.CREATED)
	MessageResponse reply(@PathVariable UUID id, @Valid @RequestBody ReplyRequest request,
			JwtAuthenticationToken authentication) {
		UUID agentUserId = UUID.fromString(authentication.getToken().getSubject());
		return toMessageResponse(inboxService.replyAsAgent(id, agentUserId, request.toContent()));
	}

	record NoteRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 4000) String text) {
	}

	record NoteResponse(UUID id, UUID authorUserId, String text, Instant createdAt) {
		static NoteResponse from(com.omnichannel.inbox.domain.ConversationNote note) {
			return new NoteResponse(note.getId(), note.getAuthorUserId(), note.getText(), note.getCreatedAt());
		}
	}

	@GetMapping("/conversations/{id}/notes")
	List<NoteResponse> notes(@PathVariable UUID id) {
		return inboxService.notesOf(id).stream().map(NoteResponse::from).toList();
	}

	@PostMapping("/conversations/{id}/notes")
	@ResponseStatus(HttpStatus.CREATED)
	NoteResponse addNote(@PathVariable UUID id, @Valid @RequestBody NoteRequest request,
			JwtAuthenticationToken authentication) {
		UUID authorUserId = UUID.fromString(authentication.getToken().getSubject());
		return NoteResponse.from(inboxService.addNote(id, authorUserId, request.text()));
	}

	@GetMapping("/search")
	List<com.omnichannel.inbox.SearchService.SearchHit> search(@RequestParam String q) {
		return searchService.search(q);
	}

	record ContactResponse(UUID id, String name, List<String> tags, List<IdentityResponse> identities) {
	}

	record IdentityResponse(String channelType, String externalId) {
	}

	@GetMapping("/contacts/{id}")
	ContactResponse contact(@PathVariable UUID id) {
		Contact contact = inboxService.getContact(id);
		List<IdentityResponse> identities = inboxService.identitiesOf(id)
			.stream()
			.map(identity -> new IdentityResponse(identity.getChannelType().name(), identity.getExternalId()))
			.toList();
		return new ContactResponse(contact.getId(), contact.getName(), contact.getTags(), identities);
	}

	@GetMapping("/contacts/{id}/conversations")
	List<ConversationResponse> contactHistory(@PathVariable UUID id) {
		inboxService.getContact(id); // 404 antes de listar
		return enrich(inboxService.conversationHistoryOf(id));
	}

	@ExceptionHandler(ConversationNotFoundException.class)
	ProblemDetail handleNotFound(ConversationNotFoundException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
	}
}
