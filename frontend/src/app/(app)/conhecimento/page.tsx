"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";

export default function KnowledgePage() {
  const queryClient = useQueryClient();
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");

  const { data: documents } = useQuery({ queryKey: ["knowledge"], queryFn: api.knowledge });

  const addMutation = useMutation({
    mutationFn: () => api.addKnowledge({ title, content }),
    onSuccess: () => {
      setTitle("");
      setContent("");
      queryClient.invalidateQueries({ queryKey: ["knowledge"] });
    },
  });
  const deleteMutation = useMutation({
    mutationFn: api.deleteKnowledge,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["knowledge"] }),
  });

  return (
    <div className="mx-auto max-w-3xl px-6 py-8">
      <h1 className="text-lg font-semibold">Base de conhecimento</h1>
      <p className="mt-1 text-sm text-ink-soft">
        A IA responde clientes usando apenas o que estiver aqui. Quanto melhor o conteúdo, maior a
        confiança das respostas.
      </p>

      <form
        onSubmit={(event) => {
          event.preventDefault();
          addMutation.mutate();
        }}
        className="mt-6 flex flex-col gap-3 rounded-lg border border-line bg-paper-raised p-4"
      >
        <input
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          placeholder="Título (ex.: Política de trocas)"
          required
          className="rounded-md border border-line bg-paper px-3 py-2 text-sm outline-none focus:border-accent focus:ring-2 focus:ring-accent-soft"
        />
        <textarea
          value={content}
          onChange={(event) => setContent(event.target.value)}
          placeholder="Conteúdo — escreva como explicaria a um atendente novo."
          rows={6}
          required
          className="resize-y rounded-md border border-line bg-paper px-3 py-2 text-sm outline-none focus:border-accent focus:ring-2 focus:ring-accent-soft"
        />
        <div className="flex items-center justify-between">
          {addMutation.isError ? (
            <p className="text-sm text-danger">Falha ao publicar. Verifique sua permissão.</p>
          ) : (
            <span />
          )}
          <button
            type="submit"
            disabled={addMutation.isPending}
            className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-white transition-opacity hover:opacity-90 disabled:opacity-50"
          >
            {addMutation.isPending ? "Publicando…" : "Publicar documento"}
          </button>
        </div>
      </form>

      <ul className="mt-6 flex flex-col gap-2">
        {documents?.map((document) => (
          <li
            key={document.id}
            className="flex items-center justify-between rounded-lg border border-line px-4 py-3"
          >
            <div>
              <p className="font-medium">{document.title}</p>
              <time className="font-mono text-xs text-ink-soft">
                {new Date(document.createdAt).toLocaleDateString("pt-BR")}
              </time>
            </div>
            <button
              onClick={() => deleteMutation.mutate(document.id)}
              className="text-sm text-danger underline underline-offset-2 hover:opacity-80"
            >
              Excluir
            </button>
          </li>
        ))}
        {documents?.length === 0 && (
          <li className="rounded-lg border border-dashed border-line px-4 py-6 text-center text-sm text-ink-soft">
            Nenhum documento ainda — a IA fará handoff de tudo até você publicar conteúdo.
          </li>
        )}
      </ul>
    </div>
  );
}
