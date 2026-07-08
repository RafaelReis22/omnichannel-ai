"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { ConversationStatus } from "@/lib/types";
import { ConversationList } from "@/components/inbox/conversation-list";
import { ConversationView } from "@/components/inbox/conversation-view";

const FILTERS: { value: ConversationStatus | ""; label: string }[] = [
  { value: "", label: "Todas" },
  { value: "OPEN", label: "Aguardando" },
  { value: "WITH_AI", label: "Com IA" },
  { value: "WITH_HUMAN", label: "Com agente" },
  { value: "RESOLVED", label: "Resolvidas" },
];

export default function InboxPage() {
  const [status, setStatus] = useState<ConversationStatus | "">("");
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ["conversations", status],
    queryFn: () => api.conversations({ status: status || undefined }),
    refetchInterval: 5_000,
  });

  return (
    <div className="grid h-dvh grid-cols-[22rem_1fr]">
      <section className="flex min-h-0 flex-col border-r border-line">
        <header className="border-b border-line px-4 pt-5 pb-3">
          <h1 className="text-lg font-semibold">Inbox</h1>
          <div className="mt-3 flex flex-wrap gap-1.5" role="tablist" aria-label="Filtro de status">
            {FILTERS.map((filter) => (
              <button
                key={filter.value}
                role="tab"
                aria-selected={status === filter.value}
                onClick={() => setStatus(filter.value)}
                className={`rounded-full px-2.5 py-1 text-xs font-medium transition-colors ${
                  status === filter.value
                    ? "bg-accent text-white"
                    : "bg-paper-raised text-ink-soft hover:bg-accent-soft hover:text-accent-ink"
                }`}
              >
                {filter.label}
              </button>
            ))}
          </div>
        </header>
        <ConversationList
          conversations={data?.content ?? []}
          isLoading={isLoading}
          selectedId={selectedId}
          onSelect={setSelectedId}
        />
      </section>
      <section className="min-h-0 min-w-0">
        {selectedId ? (
          <ConversationView conversationId={selectedId} />
        ) : (
          <div className="flex h-full items-center justify-center text-ink-soft">
            <p>Selecione uma conversa para atender.</p>
          </div>
        )}
      </section>
    </div>
  );
}
