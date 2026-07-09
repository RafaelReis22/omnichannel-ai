package com.omnichannel.inbox.realtime;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.omnichannel.inbox.ConversationActivity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Push do inbox ao vivo: uma sessão WebSocket por aba do dashboard,
 * agrupadas por tenant (atributo definido no handshake autenticado).
 * O payload é mínimo — {@code {"type":"conversation","conversationId":...}}
 * — o cliente reconsulta pela API, que aplica RLS e autorização.
 *
 * <p>Estado em memória: suficiente para uma instância (docs/07-deploy.md);
 * multi-instância exigirá relay via Redis pub/sub — gatilho documentado em
 * docs/05-escalabilidade.md.
 */
@Component
public class InboxRealtimeHandler extends TextWebSocketHandler {

	static final String TENANT_ATTRIBUTE = "tenantId";

	private static final Logger log = LoggerFactory.getLogger(InboxRealtimeHandler.class);

	private final Map<UUID, Set<WebSocketSession>> sessionsByTenant = new ConcurrentHashMap<>();

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		UUID tenantId = (UUID) session.getAttributes().get(TENANT_ATTRIBUTE);
		sessionsByTenant.computeIfAbsent(tenantId, key -> ConcurrentHashMap.newKeySet()).add(session);
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		UUID tenantId = (UUID) session.getAttributes().get(TENANT_ATTRIBUTE);
		Set<WebSocketSession> sessions = sessionsByTenant.get(tenantId);
		if (sessions != null) {
			sessions.remove(session);
		}
	}

	/** AFTER_COMMIT: o cliente nunca é notificado de algo que deu rollback. */
	@TransactionalEventListener
	public void on(ConversationActivity activity) {
		Set<WebSocketSession> sessions = sessionsByTenant.get(activity.tenantId());
		if (sessions == null || sessions.isEmpty()) {
			return;
		}
		TextMessage payload = new TextMessage(
				"{\"type\":\"conversation\",\"conversationId\":\"" + activity.conversationId() + "\"}");
		for (WebSocketSession session : sessions) {
			try {
				if (session.isOpen()) {
					session.sendMessage(payload);
				}
			}
			catch (Exception e) {
				log.debug("Falha ao notificar sessão websocket: {}", e.getMessage());
			}
		}
	}
}
