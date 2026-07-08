package com.omnichannel.crm;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.omnichannel.TestcontainersConfiguration;
import com.omnichannel.inbox.domain.Contact;
import com.omnichannel.inbox.domain.ContactRepository;
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

/** Pipeline kanban: estágios padrão, negócio criado/movido, isolamento por tenant. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CrmIntegrationTest {

	@Autowired
	TestRestTemplate rest;

	@Autowired
	AuthService authService;

	@Autowired
	ContactRepository contactRepository;

	@Autowired
	org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

	String token;
	UUID tenantId;
	UUID contactId;

	@BeforeEach
	void preparaTenantEContato() {
		var tokens = authService.signup("Empresa CRM", "Vendedora", "crm-" + UUID.randomUUID() + "@x.com",
				"senha-forte-1");
		token = tokens.accessToken();
		tenantId = UUID.fromString(jwtDecoder.decode(token).getClaimAsString("tenant_id"));
		TenantContext.setTenantId(tenantId);
		contactId = contactRepository.save(new Contact(tenantId, "Cliente Interessado")).getId();
		TenantContext.clear();
	}

	@Test
	void boardNasceComEstagiosPadraoENegocioAndaPeloFunil() {
		// primeiro acesso cria os estágios padrão
		Map board = get("/api/crm/board").getBody();
		List<Map> stages = (List<Map>) board.get("stages");
		assertThat(stages).extracting(stage -> stage.get("name"))
			.containsExactly("Novo", "Em conversa", "Proposta", "Fechado");

		// cria negócio (cai no primeiro estágio) com nome do contato resolvido
		ResponseEntity<Map> created = post("/api/crm/deals",
				Map.of("contactId", contactId.toString(), "title", "Plano anual", "value", 1200));
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(created.getBody()).containsEntry("contactName", "Cliente Interessado");
		String dealId = (String) created.getBody().get("id");

		// move para "Proposta"
		String propostaId = (String) stages.get(2).get("id");
		ResponseEntity<Map> moved = post("/api/crm/deals/" + dealId + "/move", Map.of("stageId", propostaId));
		assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.OK);

		// board reflete a coluna nova
		Map refreshed = get("/api/crm/board").getBody();
		List<Map> refreshedStages = (List<Map>) refreshed.get("stages");
		assertThat((List<Map>) refreshedStages.get(2).get("deals")).hasSize(1);
		assertThat((List<Map>) refreshedStages.get(0).get("deals")).isEmpty();
	}

	@Test
	void estagioComNegociosNaoPodeSerExcluido() {
		Map board = get("/api/crm/board").getBody();
		String firstStageId = (String) ((List<Map>) board.get("stages")).get(0).get("id");
		post("/api/crm/deals", Map.of("contactId", contactId.toString(), "title", "Negócio preso"));

		ResponseEntity<Map> deletion = exchange(HttpMethod.DELETE, "/api/crm/stages/" + firstStageId, null);
		assertThat(deletion.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void boardDeOutroTenantENasceVazioENaoVazaNegocios() {
		post("/api/crm/deals", Map.of("contactId", contactId.toString(), "title", "Segredo comercial"));

		String otherToken = authService
			.signup("Concorrente", "Espião", "esp-" + UUID.randomUUID() + "@x.com", "senha-forte-2")
			.accessToken();
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(otherToken);
		ResponseEntity<Map> otherBoard = rest.exchange("/api/crm/board", HttpMethod.GET, new HttpEntity<>(headers),
				Map.class);
		List<Map> stages = (List<Map>) otherBoard.getBody().get("stages");
		assertThat(stages).allSatisfy(stage -> assertThat((List<?>) stage.get("deals")).isEmpty());
	}

	@Test
	void atualizaEExcluiNegocio() {
		String dealId = (String) post("/api/crm/deals",
				Map.of("contactId", contactId.toString(), "title", "Rascunho")).getBody().get("id");

		ResponseEntity<Map> updated = exchange(HttpMethod.PUT, "/api/crm/deals/" + dealId,
				Map.of("title", "Plano trimestral", "value", new BigDecimal("450.50")));
		assertThat(updated.getBody()).containsEntry("title", "Plano trimestral");

		assertThat(exchange(HttpMethod.DELETE, "/api/crm/deals/" + dealId, null).getStatusCode())
			.isEqualTo(HttpStatus.NO_CONTENT);
	}

	private ResponseEntity<Map> get(String path) {
		return exchange(HttpMethod.GET, path, null);
	}

	private ResponseEntity<Map> post(String path, Object body) {
		return exchange(HttpMethod.POST, path, body);
	}

	private ResponseEntity<Map> exchange(HttpMethod method, String path, Object body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return rest.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
	}
}
