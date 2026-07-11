"use client";

import type { Conversation } from "@/lib/types";
import { StatusChip } from "@/components/ui/status-chip";

const CHANNEL_LABEL: Record<string, string> = {
  WEBCHAT: "Chat",
  WHATSAPP: "WhatsApp",
  EMAIL: "E-mail",
  INSTAGRAM: "Instagram",
  MESSENGER: "Messenger",
};

function relativeTime(iso: string | null): string {
  if (!iso) return "";
  const diffMs = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(diffMs / 60_000);
  if (minutes < 1) return "agora";
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} h`;
  return `${Math.floor(hours / 24)} d`;
}

export function ConversationList({
  conversations,
  isLoading,
  selectedId,
  onSelect,
}: {
  conversations: Conversation[];
  isLoading: boolean;
  selectedId: string | null;
  onSelect: (id: string) => void;
}) {
  if (isLoading) {
    return <p className="p-4 text-sm text-ink-soft">Carregando…</p>;
  }
  if (conversations.length === 0) {
    return (
      <div className="flex flex-1 items-center justify-center p-6 text-center text-sm text-ink-soft">
        <p>
          Nenhuma conversa por aqui.
          <span className="mt-1 block">
            Conecte um canal e as mensagens dos clientes aparecem nesta fila.
          </span>
        </p>
      </div>
    );
  }
  return (
    <ul className="min-h-0 flex-1 overflow-y-auto">
      {conversations.map((conversation) => {
        const selected = conversation.id === selectedId;
        return (
          <li key={conversation.id}>
            <button
              onClick={() => onSelect(conversation.id)}
              aria-current={selected}
              className={`flex w-full flex-col gap-0.5 border-b border-line border-l-2 px-4 py-2.5 text-left transition-colors ${
                selected
                  ? "border-l-accent bg-accent-soft/60"
                  : "border-l-transparent hover:bg-paper-sunken"
              }`}
            >
              <span className="flex items-baseline justify-between gap-2">
                <span className="truncate text-sm font-semibold">{conversation.contactName}</span>
                <time className="shrink-0 font-mono text-[11px] text-ink-soft">
                  {relativeTime(conversation.lastMessageAt)}
                </time>
              </span>
              <span className="line-clamp-1 text-sm text-ink-soft">
                {conversation.lastMessagePreview ?? "(sem mensagens)"}
              </span>
              <span className="mt-0.5 flex items-center gap-2.5">
                <StatusChip status={conversation.status} />
                {conversation.channelType && (
                  <span className="text-[11px] text-ink-soft">
                    {CHANNEL_LABEL[conversation.channelType] ?? conversation.channelType}
                  </span>
                )}
                {conversation.tags.slice(0, 2).map((tag) => (
                  <span
                    key={tag}
                    className={`rounded px-1.5 py-px text-[11px] ${
                      tag === "possível venda"
                        ? "bg-st-resolved-soft font-medium text-st-resolved"
                        : "bg-paper-sunken text-ink-soft"
                    }`}
                  >
                    {tag === "possível venda" ? "💰 possível venda" : tag}
                  </span>
                ))}
              </span>
            </button>
          </li>
        );
      })}
    </ul>
  );
}
