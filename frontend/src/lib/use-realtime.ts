"use client";

import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { tokenStore } from "./auth";

const WS_URL = (process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080").replace(/^http/, "ws");
const RECONNECT_DELAY_MS = 3_000;

/**
 * Inbox ao vivo: conecta ao /ws do backend e invalida as queries da
 * conversa afetada a cada push. O polling das queries continua como rede
 * de segurança, em intervalo relaxado.
 */
export function useRealtime(enabled: boolean) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!enabled) return;
    let socket: WebSocket | null = null;
    let closedByCleanup = false;
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;

    function connect() {
      const token = tokenStore.access();
      if (!token) return;
      socket = new WebSocket(`${WS_URL}/ws?token=${encodeURIComponent(token)}`);
      socket.onmessage = (event) => {
        try {
          const payload = JSON.parse(event.data) as { type: string; conversationId?: string };
          if (payload.type === "conversation") {
            queryClient.invalidateQueries({ queryKey: ["conversations"] });
            if (payload.conversationId) {
              queryClient.invalidateQueries({ queryKey: ["conversation", payload.conversationId] });
              queryClient.invalidateQueries({ queryKey: ["messages", payload.conversationId] });
            }
          }
        } catch {
          // frame fora do contrato: ignora
        }
      };
      socket.onclose = () => {
        if (!closedByCleanup) {
          reconnectTimer = setTimeout(connect, RECONNECT_DELAY_MS);
        }
      };
    }

    connect();
    return () => {
      closedByCleanup = true;
      if (reconnectTimer) clearTimeout(reconnectTimer);
      socket?.close();
    };
  }, [enabled, queryClient]);
}
