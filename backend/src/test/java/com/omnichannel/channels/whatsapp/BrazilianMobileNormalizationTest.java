package com.omnichannel.channels.whatsapp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrazilianMobileNormalizationTest {

	@Test
	void recolocaONonoDigitoEmCelularBrasileiroDe12Digitos() {
		// wa_id sem o 9 (como a Meta entrega) → envia com o 9
		assertThat(WhatsAppChannelAdapter.normalizeBrazilianMobile("555496333879")).isEqualTo("5554996333879");
	}

	@Test
	void naoMexeEmNumerosJaCompletosFixosOuDeOutrosPaises() {
		// celular BR já com 9 (13 dígitos)
		assertThat(WhatsAppChannelAdapter.normalizeBrazilianMobile("5554996333879")).isEqualTo("5554996333879");
		// fixo BR (2-5 após o DDD não é celular)
		assertThat(WhatsAppChannelAdapter.normalizeBrazilianMobile("555430211234")).isEqualTo("555430211234");
		// EUA e nulo
		assertThat(WhatsAppChannelAdapter.normalizeBrazilianMobile("15551490141")).isEqualTo("15551490141");
		assertThat(WhatsAppChannelAdapter.normalizeBrazilianMobile(null)).isNull();
	}
}
