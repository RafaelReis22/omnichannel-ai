package com.omnichannel.crm.web;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.omnichannel.crm.AppointmentService;
import com.omnichannel.crm.CrmService;
import com.omnichannel.crm.domain.Appointment;
import com.omnichannel.inbox.domain.Contact;
import com.omnichannel.inbox.domain.ContactRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agendamentos: criação a partir do atendimento, agenda por período e
 * convite .ics — formato universal que Google/Outlook/Apple importam,
 * dispensando OAuth de calendário nesta fase (docs/02).
 */
@RestController
@RequestMapping("/api/appointments")
class AppointmentController {

	private static final DateTimeFormatter ICS_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
		.withZone(ZoneOffset.UTC);

	private final AppointmentService appointmentService;
	private final ContactRepository contactRepository;

	AppointmentController(AppointmentService appointmentService, ContactRepository contactRepository) {
		this.appointmentService = appointmentService;
		this.contactRepository = contactRepository;
	}

	record CreateRequest(
			@NotNull UUID contactId,
			UUID conversationId,
			@NotBlank @Size(max = 200) String title,
			@Size(max = 2000) String notes,
			@NotNull Instant scheduledAt,
			@Min(5) @Max(480) Integer durationMinutes) {
	}

	record AppointmentResponse(UUID id, UUID contactId, String contactName, UUID conversationId, String title,
			String notes, Instant scheduledAt, int durationMinutes, String status, Instant createdAt) {
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	AppointmentResponse create(@Valid @RequestBody CreateRequest request, JwtAuthenticationToken authentication) {
		UUID tenantId = UUID.fromString(authentication.getToken().getClaimAsString("tenant_id"));
		UUID userId = UUID.fromString(authentication.getToken().getSubject());
		contactRepository.findById(request.contactId())
			.orElseThrow(() -> new CrmService.CrmNotFoundException("Contato não encontrado"));
		Appointment appointment = appointmentService.schedule(tenantId, request.contactId(),
				request.conversationId(), userId, request.title(), request.notes(), request.scheduledAt(),
				request.durationMinutes() == null ? 30 : request.durationMinutes());
		return toResponse(appointment, Map.of());
	}

	/** Agenda do período — padrão: dos últimos 7 aos próximos 30 dias. */
	@GetMapping
	List<AppointmentResponse> agenda(@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to) {
		Instant start = from == null ? Instant.now().minus(7, ChronoUnit.DAYS) : from;
		Instant end = to == null ? Instant.now().plus(30, ChronoUnit.DAYS) : to;
		List<Appointment> appointments = appointmentService.agenda(start, end);
		Map<UUID, String> names = namesOf(appointments);
		return appointments.stream().map(appointment -> toResponse(appointment, names)).toList();
	}

	@GetMapping("/by-conversation/{conversationId}")
	List<AppointmentResponse> ofConversation(@PathVariable UUID conversationId) {
		List<Appointment> appointments = appointmentService.ofConversation(conversationId);
		Map<UUID, String> names = namesOf(appointments);
		return appointments.stream().map(appointment -> toResponse(appointment, names)).toList();
	}

	@PostMapping("/{id}/done")
	AppointmentResponse done(@PathVariable UUID id) {
		return toResponse(appointmentService.markDone(id), Map.of());
	}

	@PostMapping("/{id}/cancel")
	AppointmentResponse cancel(@PathVariable UUID id) {
		return toResponse(appointmentService.cancel(id), Map.of());
	}

	/** Convite .ics para baixar/encaminhar — qualquer calendário importa. */
	@GetMapping(value = "/{id}/ics")
	ResponseEntity<String> ics(@PathVariable UUID id) {
		Appointment appointment = appointmentService.get(id);
		String contactName = contactRepository.findById(appointment.getContactId())
			.map(Contact::getName)
			.orElse("Contato");
		String ics = """
				BEGIN:VCALENDAR\r
				VERSION:2.0\r
				PRODID:-//omnichannel-ai//PT-BR\r
				METHOD:PUBLISH\r
				BEGIN:VEVENT\r
				UID:%s@omnichannel-ai\r
				DTSTAMP:%s\r
				DTSTART:%s\r
				DTEND:%s\r
				SUMMARY:%s\r
				DESCRIPTION:%s\r
				END:VEVENT\r
				END:VCALENDAR\r
				""".formatted(appointment.getId(), ICS_STAMP.format(Instant.now()),
				ICS_STAMP.format(appointment.getScheduledAt()),
				ICS_STAMP.format(appointment.getScheduledAt()
					.plus(appointment.getDurationMinutes(), ChronoUnit.MINUTES)),
				icsEscape(appointment.getTitle() + " — " + contactName),
				icsEscape(appointment.getNotes() == null ? "" : appointment.getNotes()));
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"agendamento.ics\"")
			.body(ics);
	}

	private Map<UUID, String> namesOf(List<Appointment> appointments) {
		return contactRepository
			.findAllById(appointments.stream().map(Appointment::getContactId).distinct().toList())
			.stream()
			.collect(Collectors.toMap(Contact::getId, Contact::getName));
	}

	private AppointmentResponse toResponse(Appointment appointment, Map<UUID, String> names) {
		String name = names.containsKey(appointment.getContactId()) ? names.get(appointment.getContactId())
				: contactRepository.findById(appointment.getContactId()).map(Contact::getName).orElse("?");
		return new AppointmentResponse(appointment.getId(), appointment.getContactId(), name,
				appointment.getConversationId(), appointment.getTitle(), appointment.getNotes(),
				appointment.getScheduledAt(), appointment.getDurationMinutes(), appointment.getStatus().name(),
				appointment.getCreatedAt());
	}

	/** Escapa vírgula, ponto e vírgula e quebras de linha (RFC 5545). */
	private String icsEscape(String value) {
		return value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n");
	}

	@ExceptionHandler(CrmService.CrmNotFoundException.class)
	ProblemDetail handleNotFound(CrmService.CrmNotFoundException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
	}
}
