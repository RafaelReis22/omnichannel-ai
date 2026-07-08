"use client";

import type { Conversation } from "@/lib/types";
import { StatusChip } from "@/components/ui/status-chip";

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
              className={`flex w-full flex-col gap-1 border-b border-line px-4 py-3 text-left transition-colors ${
                selected ? "bg-accent-soft" : "hover:bg-paper-raised"
              }`}
            >
              <span className="flex items-center justify-between gap-2">
                <StatusChip status={conversation.status} />
                <time className="shrink-0 font-mono text-xs text-ink-soft">
                  {relativeTime(conversation.lastMessageAt)}
                </time>
              </span>
              <span className="line-clamp-2 text-sm text-ink">
                {conversation.lastMessagePreview ?? "(sem mensagens)"}
              </span>
              {conversation.tags.length > 0 && (
                <span className="flex flex-wrap gap-1">
                  {conversation.tags.map((tag) => (
                    <span
                      key={tag}
                      className="rounded bg-paper-raised px-1.5 py-0.5 text-[11px] text-ink-soft"
                    >
                      {tag}
                    </span>
                  ))}
                </span>
              )}
            </button>
          </li>
        );
      })}
    </ul>
  );
}
