package com.omnichannel;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifica as fronteiras do monólito modular (docs/03-arquitetura.md): um
 * módulo nunca acessa internals de outro. Falha o build em caso de violação —
 * é o que mantém a extração futura de serviços como refactor, não reescrita.
 */
class ModulithArchitectureTest {

	static final ApplicationModules modules = ApplicationModules.of(OmnichannelBackendApplication.class);

	@Test
	void fronteirasDeModuloSaoRespeitadas() {
		modules.verify();
	}

	@Test
	void geraDocumentacaoDosModulos() {
		// Gera diagramas C4/PlantUML em target/spring-modulith-docs — insumo
		// para manter docs/03-arquitetura.md fiel ao código.
		new Documenter(modules).writeDocumentation();
	}
}
