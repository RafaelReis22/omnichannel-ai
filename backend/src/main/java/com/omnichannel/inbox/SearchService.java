package com.omnichannel.inbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Busca global do inbox: full-text (tsvector, português) sobre mensagens e
 * nomes de contato. As queries rodam na conexão da requisição — o RLS já
 * escopa tudo ao tenant do JWT; nenhum filtro manual de tenant aqui.
 */
@Service
public class SearchService {

	private static final int LIMIT = 20;

	public record SearchHit(String type, UUID conversationId, UUID contactId, String snippet, Instant occurredAt) {
	}

	private final JdbcTemplate jdbcTemplate;

	public SearchService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public List<SearchHit> search(String query) {
		if (query == null || query.strip().length() < 2) {
			return List.of();
		}
		String q = query.strip();
		return jdbcTemplate.query("""
				(SELECT 'MESSAGE' AS type, m.conversation_id, c.contact_id,
				        left(m.text, 160) AS snippet, m.created_at AS occurred_at
				   FROM messages m
				   JOIN conversations c ON c.id = m.conversation_id
				  WHERE to_tsvector('portuguese', coalesce(m.text, '')) @@ plainto_tsquery('portuguese', ?))
				UNION ALL
				(SELECT 'CONTACT' AS type, NULL AS conversation_id, ct.id AS contact_id,
				        ct.name AS snippet, ct.created_at AS occurred_at
				   FROM contacts ct
				  WHERE to_tsvector('portuguese', ct.name) @@ plainto_tsquery('portuguese', ?))
				ORDER BY occurred_at DESC
				LIMIT %d
				""".formatted(LIMIT),
				(rs, rowNum) -> new SearchHit(
						rs.getString("type"),
						rs.getObject("conversation_id", UUID.class),
						rs.getObject("contact_id", UUID.class),
						rs.getString("snippet"),
						rs.getTimestamp("occurred_at").toInstant()),
				q, q);
	}
}
