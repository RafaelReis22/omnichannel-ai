"use client";

import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { Message } from "@/lib/types";
import { StatusChip } from "@/components/ui/status-chip";

export function ConversationView({ conversationId }: { conversationId: string }) {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState("");
  const scrollRef = useRef<HTMLDivElement>(null);

  const { data: conversation } = useQuery({
    queryKey: ["conversation", conversationId],
    queryFn: () => api.conversation(conversationId),
    refetchInterval: 30_000,
  });
  const { data: contact } = useQuery({
    queryKey: ["contact", conversation?.contactId],
    queryFn: () => api.contact(conversation!.contactId),
    enabled: !!conversation,
  });
  const { data: messages } = useQuery({
    queryKey: ["messages", conversationId],
    queryFn: () => api.messages(conversationId),
    refetchInterval: 30_000,
  });
  const { data: me } = useQuery({ queryKey: ["me"], queryFn: api.me });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["messages", conversationId] });
    queryClient.invalidateQueries({ queryKey: ["conversation", conversationId] });
    queryClient.invalidateQueries({ queryKey: ["conversations"] });
  };

  const replyMutation = useMutation({
    mutationFn: (text: string) => api.reply(conversationId, text),
    onSuccess: () => {
      setDraft("");
      invalidate();
    },
  });
  const assignMutation = useMutation({
    mutationFn: () => api.assign(conversationId, me!.id),
    onSuccess: invalidate,
  });
  const resolveMutation = useMutation({
    mutationFn: () => api.resolve(conversationId),
    onSuccess: invalidate,
  });
  const dealMutation = useMutation({
    mutationFn: () =>
      api.createDeal({ contactId: conversation!.contactId, title: `Negócio — ${contact?.name ?? "contato"}` }),
  });

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight });
  }, [messages?.content.length]);

  if (!conversation) return null;

  const mine = conversation.assigneeUserId !== null && conversation.assigneeUserId === me?.id;

  return (
    <div className="flex h-dvh min-h-0 flex-col">
      <header className="flex items-center justify-between gap-3 border-b border-line px-5 py-3">
        <div className="min-w-0">
          <h2 className="truncate font-semibold">{contact?.name ?? "…"}</h2>
          <p className="truncate font-mono text-xs text-ink-soft">
            {contact?.identities.map((identity) => identity.externalId).join(" · ")}
          </p>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <StatusChip status={conversation.status} />
          <button
            onClick={() => dealMutation.mutate()}
            disabled={dealMutation.isPending || dealMutation.isSuccess}
            title="Cria um negócio no pipeline com este contato"
            className="rounded-md border border-line bg-paper-raised px-3 py-1.5 text-sm font-medium transition-colors hover:border-accent hover:text-accent-ink disabled:opacity-50"
          >
            {dealMutation.isSuccess ? "No pipeline ✓" : "+ Negócio"}
          </button>
          {!mine && conversation.status !== "RESOLVED" && (
            <button
              onClick={() => assignMutation.mutate()}
              disabled={assignMutation.isPending || !me}
              className="rounded-md border border-line bg-paper-raised px-3 py-1.5 text-sm font-medium transition-colors hover:border-accent hover:text-accent-ink disabled:opacity-50"
            >
              Assumir
            </button>
          )}
          {conversation.status !== "RESOLVED" && (
            <button
              onClick={() => resolveMutation.mutate()}
              disabled={resolveMutation.isPending}
              className="rounded-md bg-st-resolved-soft px-3 py-1.5 text-sm font-medium text-st-resolved transition-opacity hover:opacity-80 disabled:opacity-50"
            >
              Resolver
            </button>
          )}
        </div>
      </header>

      <div ref={scrollRef} className="min-h-0 flex-1 overflow-y-auto px-5 py-4">
        <ol className="flex flex-col gap-3">
          {messages?.content.map((message) => <MessageBubble key={message.id} message={message} />)}
        </ol>
      </div>

      <footer className="border-t border-line p-4">
        <form
          onSubmit={(event) => {
            event.preventDefault();
            if (draft.trim()) replyMutation.mutate(draft.trim());
          }}
          className="flex items-end gap-2"
        >
          <textarea
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                if (draft.trim()) replyMutation.mutate(draft.trim());
              }
            }}
            placeholder="Escreva sua resposta… (Enter envia, Shift+Enter quebra linha)"
            rows={2}
            className="min-h-[3rem] flex-1 resize-y rounded-md border border-line bg-paper-raised px-3 py-2 text-sm outline-none transition-colors focus:border-accent focus:ring-2 focus:ring-accent-soft"
          />
          <button
            type="submit"
            disabled={replyMutation.isPending || !draft.trim()}
            className="rounded-md bg-accent px-4 py-2.5 text-sm font-medium text-white transition-opacity hover:opacity-90 disabled:opacity-50"
          >
            Enviar
          </button>
        </form>
        {replyMutation.isError && (
          <p className="mt-2 text-sm text-danger">Falha ao enviar. Tente novamente.</p>
        )}
      </footer>
    </div>
  );
}

function MessageBubble({ message }: { message: Message }) {
  const fromContact = message.sender === "CONTACT";
  const fromAi = message.sender === "AI";
  return (
    <li className={`flex ${fromContact ? "justify-start" : "justify-end"}`}>
      <div
        className={`max-w-[70%] rounded-lg px-3.5 py-2 text-sm ${
          fromContact
            ? "bg-paper-raised text-ink"
            : fromAi
              ? "border border-st-ai/30 bg-st-ai-soft text-ink"
              : "bg-accent text-white"
        }`}
      >
        {fromAi && <p className="mb-0.5 text-[11px] font-semibold text-st-ai">IA</p>}
        <p className="break-words whitespace-pre-wrap">{message.text ?? `[${message.kind.toLowerCase()}]`}</p>
        <time className="mt-1 block text-right font-mono text-[10px] opacity-60">
          {new Date(message.createdAt).toLocaleTimeString("pt-BR", {
            hour: "2-digit",
            minute: "2-digit",
          })}
        </time>
      </div>
    </li>
  );
}
