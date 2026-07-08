package com.omnichannel.channels;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.omnichannel.TestcontainersConfiguration;
import com.omnichannel.shared.tenancy.TenantContext;
import com.omnichannel.channels.domain.Channel;
import com.omnichannel.channels.domain.ChannelService;
import com.omnichannel.tenant.AuthService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrato do Channel Gateway: credenciais nunca em claro no banco,
 * gateway publica o evento normalizado, canal desabilitado rejeita
 * mensagem, e RLS escopa canais por tenant.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class ChannelModuleIntegrationTest {

	@Autowired
	ChannelService channelService;

	@Autowired
	InboundMessageGateway inboundMessageGateway;

	@Autowired
	AuthService authService;

	@Autowired
	JwtDecoder jwtDecoder;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	ApplicationEvents applicationEvents;

	UUID tenantId;

	@BeforeEach
	void criaTenant() {
		var tokens = authService.signup("Empresa Canais", "Admin", "canais-" + UUID.randomUUID() + "@x.com",
				"senha-forte-1");
		tenantId = UUID.fromString(jwtDecoder.decode(tokens.accessToken()).getClaimAsString("tenant_id"));
		TenantContext.setTenantId(tenantId);
	}

	@AfterEach
	void limpaContexto() {
		TenantContext.clear();
	}

	@Test
	void credenciaisNuncaVaoAoBancoEmClaro() {
		String secret = "token-super-secreto-do-whatsapp";
		Channel channel = channelService.connect(tenantId, ChannelType.WHATSAPP, "WhatsApp Principal",
				Map.of("apiKey", secret));

		String stored = jdbcTemplate.queryForObject(
				"SELECT credentials_encrypted FROM channels WHERE id = ?", String.class, channel.getId());
		assertThat(stored).doesNotContain(secret).doesNotContain("apiKey");

		// e voltam ao claro apenas via serviço
		ChannelCredentials credentials = channelService.credentialsOf(channel.getId());
		assertThat(credentials.require("apiKey")).isEqualTo(secret);
	}

	@Test
	void gatewayPublicaEventoNormalizadoParaCanalAtivo() {
		Channel channel = channelService.connect(tenantId, ChannelType.WEBCHAT, "Chat do Site", Map.of("k", "v"));
		TenantContext.clear(); // webhook não tem JWT — o gateway assume o contexto sozinho

		InboundMessageReceived message = new InboundMessageReceived(tenantId, channel.getId(), ChannelType.WEBCHAT,
				"visitante-123", "msg-1", MessageContent.text("Olá, quero saber o preço"), Instant.now());
		inboundMessageGateway.receive(message);

		List<InboundMessageReceived> published = applicationEvents.stream(InboundMessageReceived.class).toList();
		assertThat(published).hasSize(1);
		assertThat(published.getFirst().externalContactId()).isEqualTo("visitante-123");
	}

	@Test
	void canalDesabilitadoRejeitaMensagem() {
		Channel channel = channelService.connect(tenantId, ChannelType.WEBCHAT, "Chat", Map.of("k", "v"));
		channelService.disable(channel.getId());
		TenantContext.clear();

		InboundMessageReceived message = new InboundMessageReceived(tenantId, channel.getId(), ChannelType.WEBCHAT,
				"visitante-123", "msg-2", MessageContent.text("oi"), Instant.now());
		assertThatThrownBy(() -> inboundMessageGateway.receive(message))
			.isInstanceOf(ChannelNotAvailableException.class);
		assertThat(applicationEvents.stream(InboundMessageReceived.class)).isEmpty();
	}

	@Test
	void tipoDeCanalIncompativelERejeitado() {
		Channel channel = channelService.connect(tenantId, ChannelType.EMAIL, "Caixa Suporte", Map.of("k", "v"));
		TenantContext.clear();

		InboundMessageReceived message = new InboundMessageReceived(tenantId, channel.getId(), ChannelType.WHATSAPP,
				"555199999", "msg-3", MessageContent.text("oi"), Instant.now());
		assertThatThrownBy(() -> inboundMessageGateway.receive(message))
			.isInstanceOf(ChannelNotAvailableException.class);
	}

	@Test
	void canaisSaoEscopadosPorTenantPeloRls() {
		channelService.connect(tenantId, ChannelType.WEBCHAT, "Meu Chat", Map.of("k", "v"));

		var outroTenantTokens = authService.signup("Outra Empresa", "Bruno", "outro-" + UUID.randomUUID() + "@x.com",
				"senha-forte-2");
		UUID outroTenant = UUID
			.fromString(jwtDecoder.decode(outroTenantTokens.accessToken()).getClaimAsString("tenant_id"));

		TenantContext.setTenantId(outroTenant);
		assertThat(channelService.list()).isEmpty();
	}

	@Test
	void registroRejeitaTipoSemAdapter() {
		ChannelRegistry registry = new ChannelRegistry(List.of());
		assertThatThrownBy(() -> registry.adapterFor(ChannelType.WHATSAPP))
			.isInstanceOf(ChannelNotAvailableException.class);
	}
}
