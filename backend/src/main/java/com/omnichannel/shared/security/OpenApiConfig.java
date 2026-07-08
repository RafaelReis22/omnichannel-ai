package com.omnichannel.shared.security;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados do contrato OpenAPI e esquema bearer — o "Authorize" do
 * Swagger UI passa a aceitar o access token do login.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

	private static final String BEARER_SCHEME = "bearerAuth";

	@Bean
	OpenAPI omnichannelOpenApi() {
		return new OpenAPI()
			.info(new Info()
				.title("omnichannel-ai API")
				.description("Plataforma de atendimento omnichannel com IA — contrato consumido pelo dashboard "
						+ "e, na Fase 4, base da API pública.")
				.version("v0"))
			.components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
				.type(SecurityScheme.Type.HTTP)
				.scheme("bearer")
				.bearerFormat("JWT")))
			.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
	}
}
