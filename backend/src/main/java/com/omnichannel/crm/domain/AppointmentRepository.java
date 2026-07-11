package com.omnichannel.crm.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

	List<Appointment> findByScheduledAtBetweenOrderByScheduledAt(Instant from, Instant to);

	List<Appointment> findByConversationIdOrderByScheduledAt(UUID conversationId);

}
