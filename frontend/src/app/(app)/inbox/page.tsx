"use client";

import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { ConversationStatus, SearchHit } from "@/lib/types";
import { ConversationList } from "@/components/inbox/conversation-list";
import { ConversationView } from "@/components/inbox/conversation-view";

const FILTERS: { value: ConversationStatus | ""; label: string }[] = [
  { value: "", label: "Todas" },
  { value: "OPEN", label: "Aguardando" },
  { value: "WITH_AI", label: "Com IA" },
  { value: "WITH_HUMAN", label: "Com agente" },
  { value: "RESOLVED", label: "Resolvidas" },
];

function useDebounced(value: string, delayMs: number): string {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(id);
  }, [value, delayMs]);
  return debounced;
}

export default function InboxPage() {
  const [status, setStatus] = useState<ConversationStatus | "">("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const debouncedQuery = useDebounced(query, 300);

  const { data, isLoading } = useQuery({
    queryKey: ["conversations", status],
    queryFn: () => api.conversations({ status: status || undefined }),
    refetchInterval: 30_000,
  });
  const { data: hits } = useQuery({
    queryKey: ["search", debouncedQuery],
    queryFn: () => api.search(debouncedQuery),
    enabled: debouncedQuery.trim().length >= 2,
  });
  const searching = query.trim().length >= 2;

  return (
    <div className="grid h-dvh grid-cols-[22rem_1fr]">
      <section className="flex min-h-0 flex-col border-r border-line">
        <header className="border-b border-line px-4 pt-5 pb-3">
          <h1 className="text-lg font-semibold">Inbox</h1>
          <input
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Buscar mensagens e contatos…"
            aria-label="Busca global"
            className="mt-3 w-full rounded-md border border-line bg-paper-raised px-3 py-1.5 text-sm outline-none transition-colors focus:border-accent focus:ring-2 focus:ring-accent-soft"
          />
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
        {searching ? (
          <SearchResults hits={hits ?? []} onOpen={(id) => { setSelectedId(id); setQuery(""); }} />
        ) : (
          <ConversationList
            conversations={data?.content ?? []}
            isLoading={isLoading}
            selectedId={selectedId}
            onSelect={setSelectedId}
          />
        )}
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

function SearchResults({ hits, onOpen }: { hits: SearchHit[]; onOpen: (conversationId: string) => void }) {
  if (hits.length === 0) {
    return <p className="p-4 text-sm text-ink-soft">Nada encontrado.</p>;
  }
  return (
    <ul className="min-h-0 flex-1 overflow-y-auto">
      {hits.map((hit) => (
        <li key={`${hit.type}-${hit.contactId}-${hit.occurredAt}`}>
          <button
            onClick={() => hit.conversationId && onOpen(hit.conversationId)}
            disabled={!hit.conversationId}
            className="flex w-full flex-col gap-0.5 border-b border-line px-4 py-3 text-left transition-colors hover:bg-paper-raised disabled:cursor-default"
          >
            <span className="text-[11px] font-semibold tracking-wide text-accent-ink uppercase">
              {hit.type === "MESSAGE" ? "Mensagem" : "Contato"}
            </span>
            <span className="line-clamp-2 text-sm">{hit.snippet}</span>
          </button>
        </li>
      ))}
    </ul>
  );
}
