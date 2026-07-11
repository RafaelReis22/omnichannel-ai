"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { AnalyticsOverview } from "@/lib/types";

const CHANNEL_LABEL: Record<string, string> = {
  WEBCHAT: "Chat do site",
  WHATSAPP: "WhatsApp",
  EMAIL: "E-mail",
  INSTAGRAM: "Instagram",
  MESSENGER: "Messenger",
};

const STATUS_LABEL: Record<string, string> = {
  OPEN: "Aguardando",
  WITH_AI: "Com IA",
  WITH_HUMAN: "Com agente",
  RESOLVED: "Resolvida",
};

const PERIODS = [
  { days: 7, label: "7 dias" },
  { days: 30, label: "30 dias" },
  { days: 90, label: "90 dias" },
] as const;

export default function RelatoriosPage() {
  const [days, setDays] = useState(30);
  const { data } = useQuery({
    queryKey: ["analytics", days],
    queryFn: () => api.analyticsOverview(days),
  });

  return (
    <div className="mx-auto max-w-4xl px-6 py-8">
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-lg font-semibold">Relatórios</h1>
          <p className="mt-1 text-sm text-ink-soft">Visão operacional do atendimento no período.</p>
        </div>
        <div className="flex rounded-md border border-line bg-paper-raised p-0.5">
          {PERIODS.map((period) => (
            <button
              key={period.days}
              onClick={() => setDays(period.days)}
              className={`rounded px-3 py-1 text-sm transition-colors ${
                days === period.days ? "bg-accent font-medium text-white" : "text-ink-soft hover:text-ink"
              }`}
            >
              {period.label}
            </button>
          ))}
        </div>
      </div>

      {data && (
        <>
          <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
            <MetricCard label="Novas conversas" value={String(data.newConversations)} />
            <MetricCard label="Mensagens recebidas" value={String(data.inboundMessages)} />
            <MetricCard label="Resolvidas" value={String(data.resolvedConversations)} />
            <MetricCard
              label="Autonomia da IA"
              value={autonomyRate(data)}
              hint={`${data.aiAnswered} respostas · ${data.aiHandoffs} handoffs`}
            />
            <MetricCard label="1ª resposta (mediana)" value={firstResponse(data.medianFirstResponseSeconds)} />
          </div>

          <section className="mt-8">
            <h2 className="text-sm font-semibold">Novas conversas por dia</h2>
            <DailyBars data={data} />
          </section>

          <div className="mt-8 grid gap-8 sm:grid-cols-2">
            <section>
              <h2 className="text-sm font-semibold">Por canal</h2>
              <DistributionList items={data.byChannelType} labels={CHANNEL_LABEL} />
            </section>
            <section>
              <h2 className="text-sm font-semibold">Por status</h2>
              <DistributionList items={data.byStatus} labels={STATUS_LABEL} />
            </section>
          </div>
        </>
      )}
    </div>
  );
}

function autonomyRate(data: AnalyticsOverview): string {
  const total = data.aiAnswered + data.aiHandoffs;
  if (total === 0) return "—";
  return `${Math.round((data.aiAnswered / total) * 100)}%`;
}

function firstResponse(seconds: number | null): string {
  if (seconds === null) return "—";
  if (seconds < 60) return `${seconds}s`;
  if (seconds < 3600) return `${Math.round(seconds / 60)}min`;
  return `${(seconds / 3600).toFixed(1)}h`;
}

function MetricCard({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="rounded-lg border border-line bg-paper-raised px-4 py-3">
      <p className="text-xs text-ink-soft">{label}</p>
      <p className="mt-1 text-2xl font-semibold tracking-tight">{value}</p>
      {hint && <p className="mt-0.5 text-[11px] text-ink-soft">{hint}</p>}
    </div>
  );
}

function DailyBars({ data }: { data: AnalyticsOverview }) {
  if (data.conversationsPerDay.length === 0) {
    return <p className="mt-3 text-sm text-ink-soft">Sem conversas no período.</p>;
  }
  const max = Math.max(...data.conversationsPerDay.map((d) => d.count));
  return (
    <div className="mt-3 flex h-28 items-end gap-1 rounded-lg border border-line bg-paper-raised p-3">
      {data.conversationsPerDay.map((day) => (
        <div
          key={day.day}
          title={`${new Date(day.day + "T12:00:00").toLocaleDateString("pt-BR")}: ${day.count}`}
          className="min-w-1.5 flex-1 rounded-t bg-accent/70 transition-colors hover:bg-accent"
          style={{ height: `${Math.max(8, (day.count / max) * 100)}%` }}
        />
      ))}
    </div>
  );
}

function DistributionList({
  items,
  labels,
}: {
  items: { label: string; count: number }[];
  labels: Record<string, string>;
}) {
  if (items.length === 0) return <p className="mt-3 text-sm text-ink-soft">Sem dados no período.</p>;
  const max = Math.max(...items.map((item) => item.count));
  return (
    <ul className="mt-3 flex flex-col gap-2">
      {items.map((item) => (
        <li key={item.label} className="flex items-center gap-3">
          <span className="w-24 shrink-0 truncate text-sm">{labels[item.label] ?? item.label}</span>
          <div className="h-4 flex-1 overflow-hidden rounded bg-paper-sunken">
            <div className="h-full rounded bg-accent/70" style={{ width: `${(item.count / max) * 100}%` }} />
          </div>
          <span className="w-8 shrink-0 text-right font-mono text-sm">{item.count}</span>
        </li>
      ))}
    </ul>
  );
}
