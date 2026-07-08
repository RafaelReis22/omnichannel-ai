package com.omnichannel.ai.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Divisor de documentos em chunks para embedding: quebra por parágrafo,
 * agrupando até ~{@value #MAX_CHUNK_CHARS} caracteres. Deliberadamente
 * simples (KISS) — trocar por chunking semântico só quando houver
 * evidência de retrieval ruim em base real.
 */
public final class KnowledgeChunker {

	private static final int MAX_CHUNK_CHARS = 1200;

	private KnowledgeChunker() {
	}

	public static List<String> chunk(String content) {
		List<String> chunks = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (String paragraph : content.split("\\n\\s*\\n")) {
			String trimmed = paragraph.strip();
			if (trimmed.isEmpty()) {
				continue;
			}
			if (current.length() + trimmed.length() > MAX_CHUNK_CHARS && !current.isEmpty()) {
				chunks.add(current.toString());
				current = new StringBuilder();
			}
			if (!current.isEmpty()) {
				current.append("\n\n");
			}
			current.append(trimmed);
			// parágrafo sozinho maior que o limite: corta duro em pedaços
			while (current.length() > MAX_CHUNK_CHARS) {
				chunks.add(current.substring(0, MAX_CHUNK_CHARS));
				current = new StringBuilder(current.substring(MAX_CHUNK_CHARS));
			}
		}
		if (!current.isEmpty()) {
			chunks.add(current.toString());
		}
		return chunks;
	}
}
