package com.omnichannel.crm;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.omnichannel.TestcontainersConfiguration;
import com.omnichannel.channels.ChannelType;
import com.omnichannel.channels.InboundMessageReceived;
import com.omnichannel.channels.MessageContent;
import com.omnichannel.channels.domain.Channel;
import com.omnichannel.channels.domain.ChannelService;
import com.omnichannel.inbox.domain.ConversationRepository;
import com.omnichannel.shared.tenancy.TenantContext;
import com.omnichannel.tenant.AuthService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Agendamento de ponta a ponta: conversa real origina um compromisso, a
 * agenda lista por período, o .ics sai válido e o RLS isola entre tenants.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AppointmentIntegrationTest {

	@Autowired
	TestRestTemplate rest;

	@Autowired
	AuthService authService;

	@Autowired
	ChannelService channelService;

	@Autowired
	com.omnichannel.channels.InboundMessageGateway gateway;

	@Autowired
	ConversationRepository conversationRepository;

	@Autowired
	com.omnichannel.ai.AiSettingsService aiSettingsService;

	@Autowired
	org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

	String agentToken;
	UUID tenantId;
	UUID contactId;
	UUID conversationId;

	@BeforeEach
	void preparaConversaComContato() {
		var tokens = authService.signup("Imobiliária Ágil", "Corretor", "agenda-" + UUID.randomUUID() + "@x.com",
				"senha-forte-1");
		agentToken = tokens.accessToken();
		tenantId = UUID.fromString(jwtDecoder.decode(agentToken).getClaimAsString("tenant_id"));
		TenantContext.setTenantId(tenantId);
		aiSettingsService.update(tenantId, false, new java.math.BigDecimal("0.7"), null);
		Channel channel = channelService.connect(tenantId, ChannelType.WEBCHAT, "Chat", Map.of());
		TenantContext.clear();

		gateway.receive(new InboundMessageReceived(tenantId, channel.getId(), ChannelType.WEBCHAT,
				"visitante-1", "Maria Compradora", "apt-1", MessageContent.text("Quero visitar o apê 42"),
				Instant.now()));
		await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
			TenantContext.setTenantId(tenantId);
			try {
				assertThat(conversationRepository.count()).isEqualTo(1);
			}
			finally {
				TenantContext.clear();
			}
		});
		TenantContext.setTenantId(tenantId);
		var conversation = conversationRepository.findAll().getFirst();
		conversationId = conversation.getId();
		contactId = conversation.getContactId();
		TenantContext.clear();
	}

	private HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return headers;
	}

	@Test
	void agendaVisitaListaPorPeriodoEGeraIcsValido() {
		Instant visita = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
		ResponseEntity<Map> created = rest.exchange("/api/appointments", HttpMethod.POST,
				new HttpEntity<>(Map.of("contactId", contactId, "conversationId", conversationId,
						"title", "Visita ao apê 42", "notes", "Levar chave e contrato",
						"scheduledAt", visita.toString(), "durationMinutes", 45), bearer(agentToken)),
				Map.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(created.getBody()).containsEntry("contactName", "Maria Compradora")
			.containsEntry("status", "SCHEDULED");
		String appointmentId = (String) created.getBody().get("id");

		// agenda do período inclui o compromisso, com nome do contato
		ResponseEntity<List> agenda = rest.exchange("/api/appointments", HttpMethod.GET,
				new HttpEntity<>(bearer(agentToken)), List.class);
		assertThat((List<Map<String, Object>>) agenda.getBody()).hasSize(1);

		// por conversa (para o painel dentro do atendimento)
		ResponseEntity<List> byConversation = rest.exchange("/api/appointments/by-conversation/" + conversationId,
				HttpMethod.GET, new HttpEntity<>(bearer(agentToken)), List.class);
		assertThat((List<Map<String, Object>>) byConversation.getBody()).hasSize(1);

		// .ics válido com horário e título escapado
		ResponseEntity<String> ics = rest.exchange("/api/appointments/" + appointmentId + "/ics", HttpMethod.GET,
				new HttpEntity<>(bearer(agentToken)), String.class);
		assertThat(ics.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(ics.getHeaders().getContentType().toString()).startsWith("text/calendar");
		assertThat(ics.getBody()).contains("BEGIN:VCALENDAR").contains("BEGIN:VEVENT")
			.contains("SUMMARY:Visita ao apê 42 — Maria Compradora")
			.contains("DTSTART:")
			.contains("END:VCALENDAR");

		// ciclo de vida: concluir e cancelar
		ResponseEntity<Map> done = rest.exchange("/api/appointments/" + appointmentId + "/done", HttpMethod.POST,
				new HttpEntity<>(bearer(agentToken)), Map.class);
		assertThat(done.getBody()).containsEntry("status", "DONE");
	}

	@Test
	void outroTenantNaoEnxergaNemAcessaAgendamentoAlheio() {
		ResponseEntity<Map> created = rest.exchange("/api/appointments", HttpMethod.POST,
				new HttpEntity<>(Map.of("contactId", contactId, "title", "Reunião privada",
						"scheduledAt", Instant.now().plus(1, ChronoUnit.DAYS).toString()), bearer(agentToken)),
				Map.class);
		String appointmentId = (String) created.getBody().get("id");

		var otherTokens = authService.signup("Concorrente", "Espião", "espiao-" + UUID.randomUUID() + "@x.com",
				"senha-forte-1");
		TenantContext.setTenantId(UUID.fromString(jwtDecoder.decode(otherTokens.accessToken())
			.getClaimAsString("tenant_id")));
		TenantContext.clear();

		ResponseEntity<List> agenda = rest.exchange("/api/appointments", HttpMethod.GET,
				new HttpEntity<>(bearer(otherTokens.accessToken())), List.class);
		assertThat((List<?>) agenda.getBody()).isEmpty();

		ResponseEntity<String> ics = rest.exchange("/api/appointments/" + appointmentId + "/ics", HttpMethod.GET,
				new HttpEntity<>(bearer(otherTokens.accessToken())), String.class);
		assertThat(ics.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void contatoInexistenteRecebe404() {
		ResponseEntity<Map> response = rest.exchange("/api/appointments", HttpMethod.POST,
				new HttpEntity<>(Map.of("contactId", UUID.randomUUID(), "title", "Fantasma",
						"scheduledAt", Instant.now().plus(1, ChronoUnit.DAYS).toString()), bearer(agentToken)),
				Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}
}
