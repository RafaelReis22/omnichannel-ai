package com.omnichannel.crm;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.omnichannel.crm.domain.Appointment;
import com.omnichannel.crm.domain.AppointmentRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agendamento de reuniões/visitas (Fase 2). Compromissos vivem ligados ao
 * contato e, quando criados de dentro do atendimento, à conversa de origem;
 * tudo escopado pelo RLS.
 */
@Service
public class AppointmentService {

	private final AppointmentRepository repository;

	public AppointmentService(AppointmentRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public Appointment schedule(UUID tenantId, UUID contactId, UUID conversationId, UUID createdByUserId,
			String title, String notes, Instant scheduledAt, int durationMinutes) {
		return repository.save(new Appointment(tenantId, contactId, conversationId, createdByUserId, title, notes,
				scheduledAt, durationMinutes));
	}

	@Transactional(readOnly = true)
	public List<Appointment> agenda(Instant from, Instant to) {
		return repository.findByScheduledAtBetweenOrderByScheduledAt(from, to);
	}

	@Transactional(readOnly = true)
	public List<Appointment> ofConversation(UUID conversationId) {
		return repository.findByConversationIdOrderByScheduledAt(conversationId);
	}

	@Transactional(readOnly = true)
	public Appointment get(UUID id) {
		return repository.findById(id)
			.orElseThrow(() -> new CrmService.CrmNotFoundException("Agendamento não encontrado"));
	}

	@Transactional
	public Appointment markDone(UUID id) {
		Appointment appointment = get(id);
		appointment.markDone();
		return appointment;
	}

	@Transactional
	public Appointment cancel(UUID id) {
		Appointment appointment = get(id);
		appointment.cancel();
		return appointment;
	}
}
