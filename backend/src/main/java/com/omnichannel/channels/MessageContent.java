package com.omnichannel.channels;

/**
 * Conteúdo de mensagem normalizado — o formato único que inbox e IA
 * consomem, independente do canal de origem (docs/03-arquitetura.md).
 */
public record MessageContent(MessageKind kind, String text, String mediaUrl, String mimeType) {

	public MessageContent {
		if (kind == MessageKind.TEXT && (text == null || text.isBlank())) {
			throw new IllegalArgumentException("Mensagem de texto exige conteúdo");
		}
		if (kind != MessageKind.TEXT && (mediaUrl == null || mediaUrl.isBlank())) {
			throw new IllegalArgumentException("Mensagem de mídia exige mediaUrl");
		}
	}

	public static MessageContent text(String text) {
		return new MessageContent(MessageKind.TEXT, text, null, null);
	}

	/** Mídia com legenda opcional ({@code text} pode ser null). */
	public static MessageContent media(MessageKind kind, String mediaUrl, String mimeType, String caption) {
		return new MessageContent(kind, caption, mediaUrl, mimeType);
	}
}
