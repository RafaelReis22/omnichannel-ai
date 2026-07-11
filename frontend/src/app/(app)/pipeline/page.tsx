"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { DealCard, StageColumn } from "@/lib/types";

export default function PipelinePage() {
  const queryClient = useQueryClient();
  const { data: board } = useQuery({ queryKey: ["board"], queryFn: api.board, refetchInterval: 10_000 });
  const [draggingId, setDraggingId] = useState<string | null>(null);

  const moveMutation = useMutation({
    mutationFn: ({ dealId, stageId }: { dealId: string; stageId: string }) => api.moveDeal(dealId, stageId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["board"] }),
  });

  const total = board?.stages.flatMap((stage) => stage.deals).reduce((sum, deal) => sum + (deal.value ?? 0), 0);

  return (
    <div className="flex h-dvh min-h-0 flex-col px-6 py-6">
      <header className="mb-4 flex items-baseline justify-between">
        <div>
          <h1 className="text-lg font-semibold">Pipeline</h1>
          <p className="text-sm text-ink-soft">
            Arraste os negócios entre estágios. Crie negócios a partir de uma conversa no inbox.
          </p>
        </div>
        {total !== undefined && total > 0 && (
          <p className="font-mono text-sm text-accent-ink">
            {total.toLocaleString("pt-BR", { style: "currency", currency: "BRL" })} em aberto
          </p>
        )}
      </header>

      <div className="flex min-h-0 flex-1 gap-4 overflow-x-auto pb-4">
        {board?.stages.map((stage) => (
          <StageColumnView
            key={stage.id}
            stage={stage}
            draggingId={draggingId}
            onDragStart={setDraggingId}
            onDropDeal={(dealId) => moveMutation.mutate({ dealId, stageId: stage.id })}
          />
        ))}
      </div>
    </div>
  );
}

function StageColumnView({
  stage,
  draggingId,
  onDragStart,
  onDropDeal,
}: {
  stage: StageColumn;
  draggingId: string | null;
  onDragStart: (id: string | null) => void;
  onDropDeal: (dealId: string) => void;
}) {
  const [over, setOver] = useState(false);
  const stageTotal = stage.deals.reduce((sum, deal) => sum + (deal.value ?? 0), 0);

  return (
    <section
      aria-label={`Estágio ${stage.name}`}
      onDragOver={(event) => {
        event.preventDefault();
        setOver(true);
      }}
      onDragLeave={() => setOver(false)}
      onDrop={(event) => {
        event.preventDefault();
        setOver(false);
        const dealId = event.dataTransfer.getData("text/deal-id");
        if (dealId) onDropDeal(dealId);
        onDragStart(null);
      }}
      className={`flex w-72 shrink-0 flex-col rounded-lg border transition-colors ${
        over && draggingId ? "border-accent bg-accent-soft/50" : "border-line bg-paper-raised/60"
      }`}
    >
      <header className="flex items-baseline justify-between px-3.5 pt-3 pb-2">
        <h2 className="text-sm font-semibold">
          {stage.name} <span className="ml-1 font-normal text-ink-soft">{stage.deals.length}</span>
        </h2>
        {stageTotal > 0 && (
          <span className="font-mono text-xs text-ink-soft">
            {stageTotal.toLocaleString("pt-BR", { style: "currency", currency: "BRL" })}
          </span>
        )}
      </header>
      <ul className="flex min-h-16 flex-1 flex-col gap-2 overflow-y-auto px-2.5 pb-3">
        {stage.deals.map((deal) => (
          <DealCardView key={deal.id} deal={deal} dragging={draggingId === deal.id} onDragStart={onDragStart} />
        ))}
      </ul>
    </section>
  );
}

function DealCardView({
  deal,
  dragging,
  onDragStart,
}: {
  deal: DealCard;
  dragging: boolean;
  onDragStart: (id: string | null) => void;
}) {
  const queryClient = useQueryClient();
  const deleteMutation = useMutation({
    mutationFn: () => api.deleteDeal(deal.id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["board"] }),
  });
  return (
    <li
      draggable
      onDragStart={(event) => {
        event.dataTransfer.setData("text/deal-id", deal.id);
        event.dataTransfer.effectAllowed = "move";
        onDragStart(deal.id);
      }}
      onDragEnd={() => onDragStart(null)}
      className={`group cursor-grab rounded-md border border-line bg-paper p-3 shadow-sm transition-all active:cursor-grabbing ${
        dragging ? "opacity-40" : "hover:border-accent/50 hover:shadow"
      }`}
    >
      <div className="flex items-start justify-between gap-2">
        <p className="text-sm font-medium">{deal.title}</p>
        <button
          type="button"
          onClick={() => {
            if (window.confirm(`Excluir o negócio "${deal.title}"?`)) deleteMutation.mutate();
          }}
          disabled={deleteMutation.isPending}
          aria-label={`Excluir negócio ${deal.title}`}
          title="Excluir negócio"
          className="shrink-0 rounded px-1 text-sm text-ink-soft/0 transition-colors group-hover:text-ink-soft hover:!text-danger"
        >
          ✕
        </button>
      </div>
      <p className="mt-0.5 truncate text-xs text-ink-soft">{deal.contactName}</p>
      {deal.value !== null && (
        <p className="mt-1.5 font-mono text-xs text-accent-ink">
          {Number(deal.value).toLocaleString("pt-BR", { style: "currency", currency: "BRL" })}
        </p>
      )}
    </li>
  );
}
