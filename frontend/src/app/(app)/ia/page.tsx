"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { AiSettings } from "@/lib/types";

export default function AiPage() {
  return (
    <div className="mx-auto max-w-4xl px-6 py-8">
      <SettingsCard />
      <LedgerTable />
    </div>
  );
}

function SettingsCard() {
  const { data: settings } = useQuery({ queryKey: ["ai-settings"], queryFn: api.aiSettings });
  // formulário só monta com os dados carregados: estado inicia das props,
  // sem sincronização via effect
  if (!settings) {
    return <section className="rounded-lg border border-line bg-paper-raised p-5 text-sm text-ink-soft">Carregando…</section>;
  }
  return <SettingsForm settings={settings} />;
}

function SettingsForm({ settings }: { settings: AiSettings }) {
  const queryClient = useQueryClient();
  const [enabled, setEnabled] = useState(settings.enabled);
  const [threshold, setThreshold] = useState(settings.confidenceThreshold);
  const [tone, setTone] = useState(settings.tone ?? "");

  const saveMutation = useMutation({
    mutationFn: () =>
      api.updateAiSettings({ enabled, confidenceThreshold: threshold, tone: tone || null }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["ai-settings"] }),
  });

  return (
    <section className="rounded-lg border border-line bg-paper-raised p-5">
      <h1 className="text-lg font-semibold">Autonomia da IA</h1>
      <p className="mt-1 text-sm text-ink-soft">
        Abaixo do limiar de confiança a IA nunca responde sozinha: a conversa vai para a fila humana
        com um resumo.
      </p>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          saveMutation.mutate();
        }}
        className="mt-5 flex flex-col gap-5"
      >
        <label className="flex items-center gap-3">
          <input
            type="checkbox"
            checked={enabled}
            onChange={(event) => setEnabled(event.target.checked)}
            className="size-4 accent-[var(--accent)]"
          />
          <span className="text-sm font-medium">Respostas automáticas ativadas</span>
        </label>

        <label className="flex flex-col gap-2">
          <span className="flex items-baseline justify-between text-sm">
            <span className="font-medium">Limiar de confiança</span>
            <span className="font-mono text-accent-ink">{threshold.toFixed(2)}</span>
          </span>
          <input
            type="range"
            min={0}
            max={1}
            step={0.05}
            value={threshold}
            onChange={(event) => setThreshold(Number(event.target.value))}
            className="accent-[var(--accent)]"
          />
          <span className="flex justify-between text-xs text-ink-soft">
            <span>Mais autonomia</span>
            <span>Mais handoff para humanos</span>
          </span>
        </label>

        <label className="flex flex-col gap-1.5">
          <span className="text-sm font-medium">Tom de voz (opcional)</span>
          <input
            value={tone}
            onChange={(event) => setTone(event.target.value)}
            placeholder="ex.: informal e simpático, tratando o cliente por você"
            className="rounded-md border border-line bg-paper px-3 py-2 text-sm outline-none focus:border-accent focus:ring-2 focus:ring-accent-soft"
          />
        </label>

        <div className="flex items-center justify-end gap-3">
          {saveMutation.isSuccess && <span className="text-sm text-st-resolved">Salvo.</span>}
          {saveMutation.isError && (
            <span className="text-sm text-danger">Falha ao salvar (apenas Admin).</span>
          )}
          <button
            type="submit"
            disabled={saveMutation.isPending}
            className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-white transition-opacity hover:opacity-90 disabled:opacity-50"
          >
            Salvar
          </button>
        </div>
      </form>
    </section>
  );
}

function LedgerTable() {
  const queryClient = useQueryClient();
  const { data: page } = useQuery({
    queryKey: ["ledger"],
    queryFn: () => api.ledger(),
    refetchInterval: 10_000,
  });
  const reviewMutation = useMutation({
    mutationFn: api.reviewLedger,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["ledger"] }),
  });

  return (
    <section className="mt-8">
      <h2 className="text-lg font-semibold">Livro-razão de resolução</h2>
      <p className="mt-1 text-sm text-ink-soft">
        Toda decisão autônoma da IA, auditável: o que respondeu, com que confiança, e o que entregou
        a um humano.
      </p>
      <div className="mt-4 overflow-x-auto rounded-lg border border-line">
        <table className="w-full min-w-[40rem] text-sm">
          <thead>
            <tr className="border-b border-line bg-paper-raised text-left text-xs uppercase tracking-wide text-ink-soft">
              <th className="px-4 py-2.5 font-medium">Quando</th>
              <th className="px-4 py-2.5 font-medium">Decisão</th>
              <th className="px-4 py-2.5 font-medium">Confiança</th>
              <th className="px-4 py-2.5 font-medium">Resumo do handoff</th>
              <th className="px-4 py-2.5 font-medium">Revisão</th>
            </tr>
          </thead>
          <tbody>
            {page?.content.map((entry) => (
              <tr key={entry.id} className="border-b border-line last:border-0">
                <td className="px-4 py-2.5 font-mono text-xs text-ink-soft">
                  {new Date(entry.createdAt).toLocaleString("pt-BR", {
                    day: "2-digit",
                    month: "2-digit",
                    hour: "2-digit",
                    minute: "2-digit",
                  })}
                </td>
                <td className="px-4 py-2.5">
                  <span
                    className={`rounded-full px-2 py-0.5 text-xs font-semibold ${
                      entry.action === "ANSWERED"
                        ? "bg-st-resolved-soft text-st-resolved"
                        : "bg-st-open-soft text-st-open"
                    }`}
                  >
                    {entry.action === "ANSWERED" ? "Respondeu" : "Handoff"}
                  </span>
                </td>
                <td className="px-4 py-2.5 font-mono">{Number(entry.confidence).toFixed(2)}</td>
                <td className="max-w-[16rem] truncate px-4 py-2.5 text-ink-soft">
                  {entry.handoffSummary ?? "—"}
                </td>
                <td className="px-4 py-2.5">
                  {entry.reviewedByHuman ? (
                    <span className="text-xs text-st-resolved">Revisado</span>
                  ) : (
                    <button
                      onClick={() => reviewMutation.mutate(entry.id)}
                      className="text-xs text-accent-ink underline underline-offset-2 hover:text-accent"
                    >
                      Marcar revisado
                    </button>
                  )}
                </td>
              </tr>
            ))}
            {page?.content.length === 0 && (
              <tr>
                <td colSpan={5} className="px-4 py-8 text-center text-ink-soft">
                  Nenhuma decisão registrada ainda — as entradas aparecem quando a IA atender.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}
