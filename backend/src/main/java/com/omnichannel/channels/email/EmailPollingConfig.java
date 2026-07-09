package com.omnichannel.channels.email;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita o agendamento do poller IMAP (intervalo em app.channels.email.poll-interval). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class EmailPollingConfig {
}
