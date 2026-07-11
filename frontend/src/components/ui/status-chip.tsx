import type { ConversationStatus } from "@/lib/types";

const STYLES: Record<ConversationStatus, { label: string; hint: string; dot: string; text: string }> = {
  OPEN: {
    label: "Precisa de você",
    hint: "Ninguém assumiu ainda — a IA não respondeu (ou o cliente pediu uma pessoa).",
    dot: "bg-st-open",
    text: "text-st-open",
  },
  WITH_AI: {
    label: "IA cuidando",
    hint: "A IA está respondendo sozinha. Você pode assumir a qualquer momento.",
    dot: "bg-st-ai",
    text: "text-st-ai",
  },
  WITH_HUMAN: {
    label: "Com atendente",
    hint: "Um humano assumiu esta conversa — a IA não interfere mais.",
    dot: "bg-st-human",
    text: "text-st-human",
  },
  RESOLVED: {
    label: "Resolvida",
    hint: "Conversa encerrada. Reabre sozinha se o cliente escrever de novo.",
    dot: "bg-st-resolved",
    text: "text-st-resolved",
  },
};

/** Status como ponto+texto: legível sem competir com o conteúdo. */
export function StatusChip({ status }: { status: ConversationStatus }) {
  const style = STYLES[status];
  return (
    <span title={style.hint} className={`inline-flex items-center gap-1.5 text-xs font-medium ${style.text}`}>
      <span className={`size-1.5 rounded-full ${style.dot}`} aria-hidden />
      {style.label}
    </span>
  );
}
