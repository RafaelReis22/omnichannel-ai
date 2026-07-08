import type { ConversationStatus } from "@/lib/types";

const STYLES: Record<ConversationStatus, { label: string; className: string }> = {
  OPEN: { label: "Aguardando", className: "bg-st-open-soft text-st-open" },
  WITH_AI: { label: "Com IA", className: "bg-st-ai-soft text-st-ai" },
  WITH_HUMAN: { label: "Com agente", className: "bg-st-human-soft text-st-human" },
  RESOLVED: { label: "Resolvida", className: "bg-st-resolved-soft text-st-resolved" },
};

export function StatusChip({ status }: { status: ConversationStatus }) {
  const style = STYLES[status];
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold ${style.className}`}
    >
      {style.label}
    </span>
  );
}
