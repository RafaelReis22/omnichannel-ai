import type { ConversationStatus } from "@/lib/types";

const STYLES: Record<ConversationStatus, { label: string; dot: string; text: string }> = {
  OPEN: { label: "Aguardando", dot: "bg-st-open", text: "text-st-open" },
  WITH_AI: { label: "Com IA", dot: "bg-st-ai", text: "text-st-ai" },
  WITH_HUMAN: { label: "Com agente", dot: "bg-st-human", text: "text-st-human" },
  RESOLVED: { label: "Resolvida", dot: "bg-st-resolved", text: "text-st-resolved" },
};

/** Status como ponto+texto: legível sem competir com o conteúdo. */
export function StatusChip({ status }: { status: ConversationStatus }) {
  const style = STYLES[status];
  return (
    <span className={`inline-flex items-center gap-1.5 text-xs font-medium ${style.text}`}>
      <span className={`size-1.5 rounded-full ${style.dot}`} aria-hidden />
      {style.label}
    </span>
  );
}
