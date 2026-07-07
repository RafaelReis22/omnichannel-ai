package com.omnichannel.tenant;

import java.util.UUID;

import com.omnichannel.TestcontainersConfiguration;
import com.omnichannel.shared.tenancy.TenantContext;
import com.omnichannel.tenant.domain.User;
import com.omnichannel.tenant.domain.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova a garantia central de segurança do produto (docs/06-seguranca.md):
 * com o contexto de tenant definido, o banco — não a aplicação — impede
 * qualquer leitura de dados de outro tenant.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RlsIsolationTest {

	@Autowired
	AuthService authService;

	@Autowired
	UserRepository userRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	JwtDecoder jwtDecoder;

	UUID tenantA;
	UUID tenantB;

	@BeforeEach
	void criaDoisTenants() {
		tenantA = tenantIdFromSignup(
				authService.signup("Empresa A", "Alice", uniqueEmail("alice"), "senha-forte-a"));
		tenantB = tenantIdFromSignup(authService.signup("Empresa B", "Bruno", uniqueEmail("bruno"), "senha-forte-b"));
	}

	@AfterEach
	void limpaContexto() {
		TenantContext.clear();
	}

	@Test
	void tenantSoEnxergaSeusPropriosUsuarios() {
		TenantContext.setTenantId(tenantA);
		assertThat(userRepository.findAll())
			.isNotEmpty()
			.allMatch(user -> user.getTenantId().equals(tenantA));

		TenantContext.setTenantId(tenantB);
		assertThat(userRepository.findAll())
			.isNotEmpty()
			.allMatch(user -> user.getTenantId().equals(tenantB));
	}

	@Test
	void semContextoDeTenantNenhumaLinhaEVisivelSobOPapelDeRuntime() {
		// Simula uma conexão de runtime sem contexto: SET ROLE derruba o
		// bypass de superusuário e as políticas negam tudo por padrão.
		// Tudo na MESMA conexão — SET ROLE é estado de sessão.
		Long visiveis = jdbcTemplate.execute((java.sql.Connection connection) -> {
			try (var statement = connection.createStatement()) {
				statement.execute("SET ROLE app_runtime");
				try (var resultSet = statement.executeQuery("SELECT count(*) FROM users")) {
					resultSet.next();
					return resultSet.getLong(1);
				}
			}
			finally {
				try (var reset = connection.createStatement()) {
					reset.execute("RESET ROLE");
				}
			}
		});
		assertThat(visiveis).isZero();
	}

	@Test
	void escritaCruzadaEntreTenantsEBloqueadaPeloBanco() {
		TenantContext.setTenantId(tenantA);
		long antes = userRepository.count();

		// Tentativa de gravar um usuário do tenant B dentro da sessão do
		// tenant A: o WITH CHECK da política rejeita, nada é persistido.
		try {
			userRepository.saveAndFlush(new User(tenantB, "Intruso", uniqueEmail("intruso"), "hash",
					com.omnichannel.tenant.domain.UserRole.AGENT));
		}
		catch (RuntimeException expected) {
			// qualquer exceção de persistência serve — o importante é o estado
		}

		TenantContext.clear();
		TenantContext.setTenantId(tenantB);
		assertThat(userRepository.findAll()).noneMatch(user -> user.getName().equals("Intruso"));
		TenantContext.clear();
		TenantContext.setTenantId(tenantA);
		assertThat(userRepository.count()).isEqualTo(antes);
	}

	private UUID tenantIdFromSignup(AuthTokens tokens) {
		return UUID.fromString(jwtDecoder.decode(tokens.accessToken()).getClaimAsString("tenant_id"));
	}

	private static String uniqueEmail(String prefix) {
		return prefix + "-" + UUID.randomUUID() + "@teste.com";
	}
}
