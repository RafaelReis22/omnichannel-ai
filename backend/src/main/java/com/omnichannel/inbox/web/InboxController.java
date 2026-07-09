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

	InboxController(InboxService inboxService, com.omnichannel.inbox.SearchService searchService) {
		this.inboxService = inboxService;
		this.searchService = searchService;
	}

	record ConversationResponse(UUID id, UUID contactId, UUID channelId, String status, UUID assigneeUserId,
			List<String> tags, String lastMessagePreview, Instant lastMessageAt, Instant createdAt) {
		static ConversationResponse from(Conversation c) {
			return new ConversationResponse(c.getId(), c.getContactId(), c.getChannelId(), c.getStatus().name(),
					c.getAssigneeUserId(), c.getTags(), c.getLastMessagePreview(), c.getLastMessageAt(),
					c.getCreatedAt());
		}
	}

	record MessageResponse(UUID id, String sender, String kind, String text, String mediaUrl, UUID senderUserId,
			Instant createdAt) {
		static MessageResponse from(Message m) {
			return new MessageResponse(m.getId(), m.getSender().name(), m.getKind().name(), m.getText(),
					m.getMediaUrl(), m.getSenderUserId(), m.getCreatedAt());
		}
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
		return inboxService
			.listConversations(status, assigneeUserId, PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE)))
			.map(ConversationResponse::from);
	}

	@GetMapping("/conversations/{id}")
	ConversationResponse get(@PathVariable UUID id) {
		return ConversationResponse.from(inboxService.getConversation(id));
	}

	@GetMapping("/conversations/{id}/messages")
	Page<MessageResponse> messages(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		inboxService.getConversation(id); // 404 antes de listar
		return inboxService.messagesOf(id, PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE)))
			.map(MessageResponse::from);
	}

	@PostMapping("/conversations/{id}/assign")
	ConversationResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignRequest request) {
		return ConversationResponse.from(inboxService.assign(id, request.userId()));
	}

	@PostMapping("/conversations/{id}/resolve")
	ConversationResponse resolve(@PathVariable UUID id) {
		return ConversationResponse.from(inboxService.resolve(id));
	}

	@PutMapping("/conversations/{id}/tags")
	ConversationResponse tags(@PathVariable UUID id, @Valid @RequestBody TagsRequest request) {
		return ConversationResponse.from(inboxService.replaceTags(id, request.tags()));
	}

	@PostMapping("/conversations/{id}/reply")
	@ResponseStatus(HttpStatus.CREATED)
	MessageResponse reply(@PathVariable UUID id, @Valid @RequestBody ReplyRequest request,
			JwtAuthenticationToken authentication) {
		UUID agentUserId = UUID.fromString(authentication.getToken().getSubject());
		return MessageResponse.from(inboxService.replyAsAgent(id, agentUserId, request.toContent()));
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
		return inboxService.conversationHistoryOf(id).stream().map(ConversationResponse::from).toList();
	}

	@ExceptionHandler(ConversationNotFoundException.class)
	ProblemDetail handleNotFound(ConversationNotFoundException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
	}
}
