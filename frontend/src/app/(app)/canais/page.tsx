"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { Channel } from "@/lib/types";

const TYPES = [
  { value: "WEBCHAT", label: "Chat do site" },
  { value: "WHATSAPP", label: "WhatsApp" },
  { value: "EMAIL", label: "E-mail" },
] as const;

export default function ChannelsPage() {
  const queryClient = useQueryClient();
  const [type, setType] = useState<string>("WEBCHAT");
  const [name, setName] = useState("");

  const { data: channels } = useQuery({ queryKey: ["channels"], queryFn: api.channels });

  const connectMutation = useMutation({
    mutationFn: () => api.connectChannel({ type, name, credentials: { origem: "dashboard" } }),
    onSuccess: () => {
      setName("");
      queryClient.invalidateQueries({ queryKey: ["channels"] });
    },
  });

  return (
    <div className="mx-auto max-w-3xl px-6 py-8">
      <h1 className="text-lg font-semibold">Canais</h1>
      <p className="mt-1 text-sm text-ink-soft">
        Cada canal conectado alimenta o mesmo inbox. O chat do site funciona na hora; WhatsApp e
        e-mail chegam nas próximas versões.
      </p>

      <form
        onSubmit={(event) => {
          event.preventDefault();
          connectMutation.mutate();
        }}
        className="mt-6 flex flex-wrap items-end gap-3 rounded-lg border border-line bg-paper-raised p-4"
      >
        <label className="flex flex-col gap-1.5">
          <span className="text-sm font-medium">Tipo</span>
          <select
            value={type}
            onChange={(event) => setType(event.target.value)}
            className="rounded-md border border-line bg-paper px-3 py-2 text-sm outline-none focus:border-accent"
          >
            {TYPES.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
        <label className="flex min-w-48 flex-1 flex-col gap-1.5">
          <span className="text-sm font-medium">Nome</span>
          <input
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="ex.: Chat da loja"
            required
            className="rounded-md border border-line bg-paper px-3 py-2 text-sm outline-none focus:border-accent focus:ring-2 focus:ring-accent-soft"
          />
        </label>
        <button
          type="submit"
          disabled={connectMutation.isPending}
          className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-white transition-opacity hover:opacity-90 disabled:opacity-50"
        >
          Conectar
        </button>
        {connectMutation.isError && (
          <p className="w-full text-sm text-danger">Falha ao conectar (apenas Admin).</p>
        )}
      </form>

      <ul className="mt-6 flex flex-col gap-3">
        {channels?.map((channel) => <ChannelCard key={channel.id} channel={channel} />)}
        {channels?.length === 0 && (
          <li className="rounded-lg border border-dashed border-line px-4 py-6 text-center text-sm text-ink-soft">
            Nenhum canal conectado ainda.
          </li>
        )}
      </ul>
    </div>
  );
}

function ChannelCard({ channel }: { channel: Channel }) {
  const [copied, setCopied] = useState(false);
  const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
  const snippet = `<script src="${typeof window !== "undefined" ? window.location.origin : ""}/widget.js"\n        data-channel-id="${channel.id}"\n        data-api-url="${apiUrl}"></script>`;

  return (
    <li className="rounded-lg border border-line p-4">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="font-medium">{channel.name}</p>
          <p className="font-mono text-xs text-ink-soft">
            {channel.type} · {channel.id}
          </p>
        </div>
        <span
          className={`rounded-full px-2 py-0.5 text-xs font-semibold ${
            channel.status === "ACTIVE"
              ? "bg-st-resolved-soft text-st-resolved"
              : "bg-paper-raised text-ink-soft"
          }`}
        >
          {channel.status === "ACTIVE" ? "Ativo" : channel.status}
        </span>
      </div>

      {channel.type === "WEBCHAT" && (
        <div className="mt-3">
          <p className="mb-1.5 text-xs font-medium text-ink-soft">
            Cole antes do <code>&lt;/body&gt;</code> do seu site:
          </p>
          <div className="relative">
            <pre className="overflow-x-auto rounded-md bg-panel p-3 font-mono text-xs leading-relaxed text-panel-ink">
              {snippet}
            </pre>
            <button
              onClick={() => {
                navigator.clipboard.writeText(snippet);
                setCopied(true);
                setTimeout(() => setCopied(false), 2000);
              }}
              className="absolute top-2 right-2 rounded bg-white/10 px-2 py-1 text-xs text-panel-ink transition-colors hover:bg-white/20"
            >
              {copied ? "Copiado!" : "Copiar"}
            </button>
          </div>
          <p className="mt-2 text-xs text-ink-soft">
            Testar agora:{" "}
            <a
              href={`/widget-demo.html?channelId=${channel.id}&apiUrl=${encodeURIComponent(apiUrl)}`}
              target="_blank"
              className="text-accent-ink underline underline-offset-2 hover:text-accent"
            >
              abrir página de demonstração
            </a>
          </p>
        </div>
      )}
    </li>
  );
}
