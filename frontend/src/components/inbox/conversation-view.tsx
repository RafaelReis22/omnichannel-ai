"use client";

import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { Message, UploadedMedia } from "@/lib/types";
import { StatusChip } from "@/components/ui/status-chip";

export function ConversationView({ conversationId }: { conversationId: string }) {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState("");
  const [attachment, setAttachment] = useState<(UploadedMedia & { fileName: string }) | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
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
    mutationFn: () =>
      attachment
        ? api.reply(conversationId, {
            kind: attachment.kind,
            text: draft.trim() || null,
            mediaUrl: attachment.mediaUrl,
            mimeType: attachment.contentType,
          })
        : api.reply(conversationId, { kind: "TEXT", text: draft.trim() }),
    onSuccess: () => {
      setDraft("");
      setAttachment(null);
      invalidate();
    },
  });
  const uploadMutation = useMutation({
    mutationFn: (file: File) => api.upload(file),
    onSuccess: (media, file) => setAttachment({ ...media, fileName: file.name }),
  });
  const suggestMutation = useMutation({
    mutationFn: () => api.suggestReply(conversationId),
    onSuccess: (data) => setDraft(data.suggestion),
  });
  const canSend = !!draft.trim() || !!attachment;
  const send = () => {
    if (canSend && !replyMutation.isPending) replyMutation.mutate();
  };
  const assignMutation = useMutation({
    mutationFn: () => api.assign(conversationId, me!.id),
    onSuccess: invalidate,
  });
  const resolveMutation = useMutation({
    mutationFn: () => api.resolve(conversationId),
    onSuccess: invalidate,
  });
  const archiveMutation = useMutation({
    mutationFn: () => api.archiveConversation(conversationId),
    onSuccess: invalidate,
  });
  const dealMutation = useMutation({
    mutationFn: () =>
      api.createDeal({ contactId: conversation!.contactId, title: `Negócio — ${contact?.name ?? "contato"}` }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["board"] }),
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
          <TagsEditor
            conversationId={conversationId}
            tags={conversation.tags}
            onSaved={invalidate}
          />
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <StatusChip status={conversation.status} />
          <ScheduleButton
            conversationId={conversationId}
            contactId={conversation.contactId}
            contactName={contact?.name ?? "contato"}
          />
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
          <button
            onClick={() => {
              if (window.confirm("Arquivar esta conversa? Ela some das listas e volta sozinha se o cliente escrever de novo."))
                archiveMutation.mutate();
            }}
            disabled={archiveMutation.isPending}
            title="Arquivar conversa (o histórico é preservado)"
            className="rounded-md border border-line bg-paper-raised px-3 py-1.5 text-sm text-ink-soft transition-colors hover:border-danger hover:text-danger disabled:opacity-50"
          >
            Arquivar
          </button>
        </div>
      </header>

      <div ref={scrollRef} className="min-h-0 flex-1 overflow-y-auto px-5 py-4">
        <ol className="flex flex-col gap-3">
          {messages?.content.map((message) => <MessageBubble key={message.id} message={message} />)}
        </ol>
      </div>

      <NotesPanel conversationId={conversationId} />

      <footer className="border-t border-line p-4">
        {attachment && (
          <div className="mb-2 flex items-center gap-3 rounded-md border border-line bg-paper-sunken px-3 py-2">
            {attachment.kind === "IMAGE" ? (
              // preview pré-assinado de vida curta — só para conferência antes do envio
              // eslint-disable-next-line @next/next/no-img-element
              <img src={attachment.previewUrl} alt={attachment.fileName} className="h-12 w-12 rounded object-cover" />
            ) : (
              <span className="text-lg" aria-hidden>
                📎
              </span>
            )}
            <span className="min-w-0 flex-1 truncate text-sm">{attachment.fileName}</span>
            <button
              type="button"
              onClick={() => setAttachment(null)}
              aria-label="Remover anexo"
              className="rounded px-2 py-0.5 text-sm text-ink-soft transition-colors hover:text-danger"
            >
              ✕
            </button>
          </div>
        )}
        <form
          onSubmit={(event) => {
            event.preventDefault();
            send();
          }}
          className="flex items-end gap-2"
        >
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*,video/*,audio/*,application/pdf"
            className="hidden"
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file) uploadMutation.mutate(file);
              event.target.value = "";
            }}
          />
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            disabled={uploadMutation.isPending}
            title="Anexar imagem, vídeo, áudio ou PDF (até 15 MB)"
            aria-label="Anexar arquivo"
            className="rounded-md border border-line bg-paper-raised px-3 py-2.5 text-sm transition-colors hover:border-accent hover:text-accent-ink disabled:opacity-50"
          >
            {uploadMutation.isPending ? "…" : "📎"}
          </button>
          <button
            type="button"
            onClick={() => suggestMutation.mutate()}
            disabled={suggestMutation.isPending}
            title="A IA escreve um rascunho baseado na conversa e na base de conhecimento — você edita e decide enviar"
            aria-label="Sugerir resposta"
            className="rounded-md border border-line bg-paper-raised px-3 py-2.5 text-sm transition-colors hover:border-accent hover:text-accent-ink disabled:opacity-50"
          >
            {suggestMutation.isPending ? "…" : "✨"}
          </button>
          <textarea
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                send();
              }
            }}
            placeholder={
              attachment
                ? "Legenda opcional… (Enter envia)"
                : "Escreva sua resposta… (Enter envia, Shift+Enter quebra linha)"
            }
            rows={2}
            className="min-h-[3rem] flex-1 resize-y rounded-md border border-line bg-paper-raised px-3 py-2 text-sm outline-none transition-colors focus:border-accent focus:ring-2 focus:ring-accent-soft"
          />
          <button
            type="submit"
            disabled={replyMutation.isPending || !canSend}
            className="rounded-md bg-accent px-4 py-2.5 text-sm font-medium text-white transition-opacity hover:opacity-90 disabled:opacity-50"
          >
            Enviar
          </button>
        </form>
        {replyMutation.isError && (
          <p className="mt-2 text-sm text-danger">Falha ao enviar. Tente novamente.</p>
        )}
        {uploadMutation.isError && (
          <p className="mt-2 text-sm text-danger">
            {uploadMutation.error instanceof Error ? uploadMutation.error.message : "Falha no upload."}
          </p>
        )}
        {suggestMutation.isError && (
          <p className="mt-2 text-sm text-danger">A IA não conseguiu sugerir agora — tente de novo.</p>
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
              : "bg-accent text-white shadow-sm"
        }`}
      >
        {fromAi && <p className="mb-0.5 text-[11px] font-semibold text-st-ai">IA</p>}
        <MediaContent message={message} />
        {message.text && <p className="break-words whitespace-pre-wrap">{message.text}</p>}
        {!message.text && message.kind === "TEXT" && <p className="italic opacity-60">(vazio)</p>}
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

function ScheduleButton({
  conversationId,
  contactId,
  contactName,
}: {
  conversationId: string;
  contactId: string;
  contactName: string;
}) {
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState("");
  const [when, setWhen] = useState("");
  const [duration, setDuration] = useState(30);

  const createMutation = useMutation({
    mutationFn: () =>
      api.createAppointment({
        contactId,
        conversationId,
        title: title.trim() || `Reunião — ${contactName}`,
        scheduledAt: new Date(when).toISOString(),
        durationMinutes: duration,
      }),
    onSuccess: () => setOpen(false),
  });

  return (
    <div className="relative">
      <button
        onClick={() => {
          setOpen(!open);
          if (!open) {
            setTitle(`Reunião — ${contactName}`);
            createMutation.reset();
          }
        }}
        className="rounded-md border border-line bg-paper-raised px-3 py-1.5 text-sm font-medium transition-colors hover:border-accent hover:text-accent-ink"
      >
        {createMutation.isSuccess ? "Agendado ✓" : "Agendar"}
      </button>
      {open && (
        <form
          onSubmit={(event) => {
            event.preventDefault();
            if (when) createMutation.mutate();
          }}
          className="absolute top-full right-0 z-10 mt-2 flex w-72 flex-col gap-2 rounded-lg border border-line bg-paper-raised p-3 shadow-lg"
        >
          <label className="flex flex-col gap-1 text-xs font-medium">
            Título
            <input
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              className="rounded-md border border-line bg-paper px-2 py-1.5 text-sm font-normal outline-none focus:border-accent"
            />
          </label>
          <label className="flex flex-col gap-1 text-xs font-medium">
            Quando
            <input
              type="datetime-local"
              required
              value={when}
              onChange={(event) => setWhen(event.target.value)}
              className="rounded-md border border-line bg-paper px-2 py-1.5 text-sm font-normal outline-none focus:border-accent"
            />
          </label>
          <label className="flex flex-col gap-1 text-xs font-medium">
            Duração
            <select
              value={duration}
              onChange={(event) => setDuration(Number(event.target.value))}
              className="rounded-md border border-line bg-paper px-2 py-1.5 text-sm font-normal outline-none focus:border-accent"
            >
              <option value={15}>15 min</option>
              <option value={30}>30 min</option>
              <option value={45}>45 min</option>
              <option value={60}>1 hora</option>
              <option value={90}>1h30</option>
            </select>
          </label>
          <div className="mt-1 flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setOpen(false)}
              className="rounded-md px-3 py-1.5 text-sm text-ink-soft transition-colors hover:text-ink"
            >
              Fechar
            </button>
            <button
              type="submit"
              disabled={createMutation.isPending || !when}
              className="rounded-md bg-accent px-3 py-1.5 text-sm font-medium text-white transition-opacity hover:opacity-90 disabled:opacity-50"
            >
              Marcar
            </button>
          </div>
          {createMutation.isError && <p className="text-xs text-danger">Falha ao agendar. Tente novamente.</p>}
        </form>
      )}
    </div>
  );
}

function TagsEditor({
  conversationId,
  tags,
  onSaved,
}: {
  conversationId: string;
  tags: string[];
  onSaved: () => void;
}) {
  const [adding, setAdding] = useState(false);
  const [draft, setDraft] = useState("");
  const saveMutation = useMutation({
    mutationFn: (next: string[]) => api.updateTags(conversationId, next),
    onSuccess: () => {
      setDraft("");
      setAdding(false);
      onSaved();
    },
  });

  const addTag = () => {
    const tag = draft.trim().toLowerCase();
    if (!tag || tags.includes(tag)) {
      setDraft("");
      setAdding(false);
      return;
    }
    saveMutation.mutate([...tags, tag]);
  };

  return (
    <div className="mt-1 flex flex-wrap items-center gap-1">
      {tags.map((tag) => (
        <span
          key={tag}
          className="group inline-flex items-center gap-1 rounded-full bg-paper-sunken px-2 py-0.5 text-xs text-ink-soft"
        >
          {tag}
          <button
            type="button"
            onClick={() => saveMutation.mutate(tags.filter((t) => t !== tag))}
            aria-label={`Remover tag ${tag}`}
            className="text-ink-soft/60 transition-colors hover:text-danger"
          >
            ×
          </button>
        </span>
      ))}
      {adding ? (
        <input
          autoFocus
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              event.preventDefault();
              addTag();
            }
            if (event.key === "Escape") {
              setDraft("");
              setAdding(false);
            }
          }}
          onBlur={addTag}
          placeholder="nova tag…"
          className="w-24 rounded-full border border-line bg-paper-raised px-2 py-0.5 text-xs outline-none focus:border-accent"
        />
      ) : (
        <button
          type="button"
          onClick={() => setAdding(true)}
          disabled={saveMutation.isPending}
          className="rounded-full border border-dashed border-line-strong px-2 py-0.5 text-xs text-ink-soft transition-colors hover:border-accent hover:text-accent-ink disabled:opacity-50"
        >
          + tag
        </button>
      )}
    </div>
  );
}

function MediaContent({ message }: { message: Message }) {
  if (message.kind === "TEXT" || !message.mediaUrl) return null;
  if (message.kind === "IMAGE") {
    return (
      <a href={message.mediaUrl} target="_blank" rel="noopener noreferrer" className="mb-1 block">
        {/* URL pré-assinada expira em minutos — não vale cachear via next/image */}
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={message.mediaUrl} alt="Imagem anexada" className="max-h-64 rounded-md object-contain" />
      </a>
    );
  }
  if (message.kind === "VIDEO") {
    return <video src={message.mediaUrl} controls className="mb-1 max-h-64 rounded-md" />;
  }
  if (message.kind === "AUDIO") {
    return <audio src={message.mediaUrl} controls className="mb-1 w-full min-w-48" />;
  }
  return (
    <a
      href={message.mediaUrl}
      target="_blank"
      rel="noopener noreferrer"
      className="mb-1 inline-flex items-center gap-1.5 rounded-md bg-black/10 px-2.5 py-1.5 text-sm underline underline-offset-2"
    >
      <span aria-hidden>📄</span> Abrir documento
    </a>
  );
}

function NotesPanel({ conversationId }: { conversationId: string }) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState("");
  const { data: notes } = useQuery({
    queryKey: ["notes", conversationId],
    queryFn: () => api.notes(conversationId),
    enabled: open,
  });
  const addMutation = useMutation({
    mutationFn: () => api.addNote(conversationId, draft.trim()),
    onSuccess: () => {
      setDraft("");
      queryClient.invalidateQueries({ queryKey: ["notes", conversationId] });
    },
  });

  return (
    <section className="border-t border-line bg-paper-sunken/60">
      <button
        onClick={() => setOpen(!open)}
        aria-expanded={open}
        className="flex w-full items-center justify-between px-4 py-1.5 text-xs font-medium text-ink-soft transition-colors hover:text-ink"
      >
        <span className="inline-flex items-center gap-1.5">
          <span className="size-1.5 rounded-full bg-st-open" aria-hidden />
          Notas internas{notes && notes.length > 0 ? ` (${notes.length})` : ""}
        </span>
        <span aria-hidden>{open ? "▾" : "▸"}</span>
      </button>
      {open && (
        <div className="px-4 pb-3">
          <ul className="flex max-h-40 flex-col gap-1.5 overflow-y-auto">
            {notes?.map((note) => (
              <li key={note.id} className="rounded-md bg-paper-raised px-3 py-1.5 text-sm">
                {note.text}
                <time className="ml-2 font-mono text-[10px] text-ink-soft">
                  {new Date(note.createdAt).toLocaleString("pt-BR", {
                    day: "2-digit",
                    month: "2-digit",
                    hour: "2-digit",
                    minute: "2-digit",
                  })}
                </time>
              </li>
            ))}
            {notes?.length === 0 && (
              <li className="text-xs text-ink-soft">Nenhuma nota — só a equipe vê o que for escrito aqui.</li>
            )}
          </ul>
          <form
            onSubmit={(event) => {
              event.preventDefault();
              if (draft.trim()) addMutation.mutate();
            }}
            className="mt-2 flex gap-2"
          >
            <input
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              placeholder="Adicionar nota interna…"
              className="flex-1 rounded-md border border-line bg-paper px-3 py-1.5 text-sm outline-none focus:border-accent"
            />
            <button
              type="submit"
              disabled={addMutation.isPending || !draft.trim()}
              className="rounded-md border border-line-strong bg-paper-raised px-3 py-1.5 text-sm font-medium transition-colors hover:border-accent hover:text-accent-ink disabled:opacity-50"
            >
              Anotar
            </button>
          </form>
        </div>
      )}
    </section>
  );
}
