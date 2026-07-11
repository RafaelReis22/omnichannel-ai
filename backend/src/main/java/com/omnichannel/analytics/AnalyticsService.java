package com.omnichannel.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Métricas operacionais lidas direto do banco via SQL — a conexão da
 * requisição carrega o tenant, então o RLS escopa tudo sem WHERE manual.
 * Leitura pura sobre tabelas dos outros módulos (CQRS leve): nenhuma
 * dependência Java entre módulos é criada aqui.
 */
@Service
public class AnalyticsService {

	private final JdbcTemplate jdbc;

	public AnalyticsService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public record DayCount(LocalDate day, long count) {
	}

	public record LabelCount(String label, long count) {
	}

	public record Overview(
			long newConversations,
			long resolvedConversations,
			long inboundMessages,
			long aiAnswered,
			long aiHandoffs,
			Long medianFirstResponseSeconds,
			List<DayCount> conversationsPerDay,
			List<LabelCount> byChannelType,
			List<LabelCount> byStatus) {
	}

	@Transactional(readOnly = true)
	public Overview overview(int days) {
		Instant since = Instant.now().minus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);

		long newConversations = count("SELECT count(*) FROM conversations WHERE created_at >= ?", since);
		long resolved = count("SELECT count(*) FROM conversations WHERE status = 'RESOLVED' AND updated_at >= ?",
				since);
		long inbound = count("SELECT count(*) FROM messages WHERE sender = 'CONTACT' AND created_at >= ?", since);
		long aiAnswered = count(
				"SELECT count(*) FROM resolution_ledger WHERE action = 'ANSWERED' AND created_at >= ?", since);
		long aiHandoffs = count(
				"SELECT count(*) FROM resolution_ledger WHERE action = 'HANDOFF' AND created_at >= ?", since);

		// mediana do tempo entre a primeira mensagem do contato e a primeira
		// resposta (agente ou IA) — mediana aguenta outliers de madrugada
		Long medianFirstResponse = jdbc.query("""
				SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY diff)::bigint AS median FROM (
				    SELECT EXTRACT(EPOCH FROM (
				        (SELECT min(m2.created_at) FROM messages m2
				            WHERE m2.conversation_id = c.id AND m2.sender IN ('AGENT','AI'))
				        - (SELECT min(m1.created_at) FROM messages m1
				            WHERE m1.conversation_id = c.id AND m1.sender = 'CONTACT')
				    )) AS diff
				    FROM conversations c WHERE c.created_at >= ?
				) diffs WHERE diff IS NOT NULL AND diff >= 0
				""", rs -> rs.next() ? (Long) rs.getObject("median", Long.class) : null, java.sql.Timestamp.from(since));

		List<DayCount> perDay = jdbc.query("""
				SELECT date_trunc('day', created_at) AS day, count(*) AS total
				FROM conversations WHERE created_at >= ?
				GROUP BY 1 ORDER BY 1
				""", (rs, i) -> new DayCount(rs.getTimestamp("day").toInstant().atZone(ZoneOffset.UTC).toLocalDate(),
				rs.getLong("total")), java.sql.Timestamp.from(since));

		List<LabelCount> byChannel = jdbc.query("""
				SELECT ch.type AS label, count(*) AS total
				FROM conversations c JOIN channels ch ON ch.id = c.channel_id
				WHERE c.created_at >= ?
				GROUP BY 1 ORDER BY total DESC
				""", (rs, i) -> new LabelCount(rs.getString("label"), rs.getLong("total")),
				java.sql.Timestamp.from(since));

		List<LabelCount> byStatus = jdbc.query("""
				SELECT status AS label, count(*) AS total FROM conversations
				WHERE created_at >= ? GROUP BY 1 ORDER BY total DESC
				""", (rs, i) -> new LabelCount(rs.getString("label"), rs.getLong("total")),
				java.sql.Timestamp.from(since));

		return new Overview(newConversations, resolved, inbound, aiAnswered, aiHandoffs, medianFirstResponse,
				perDay, byChannel, byStatus);
	}

	private long count(String sql, Instant since) {
		Long result = jdbc.queryForObject(sql, Long.class, java.sql.Timestamp.from(since));
		return result == null ? 0 : result;
	}
}
