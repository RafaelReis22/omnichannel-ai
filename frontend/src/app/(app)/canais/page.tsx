"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { Channel, ConnectedChannel } from "@/lib/types";

const TYPES = [
  { value: "WEBCHAT", label: "Chat do site" },
  { value: "WHATSAPP", label: "WhatsApp" },
  { value: "EMAIL", label: "E-mail" },
  { value: "INSTAGRAM", label: "Instagram" },
  { value: "MESSENGER", label: "Messenger" },
] as const;

const TYPE_LABEL: Record<string, string> = {
  WEBCHAT: "Chat do site",
  WHATSAPP: "WhatsApp",
  EMAIL: "E-mail",
  INSTAGRAM: "Instagram",
  MESSENGER: "Messenger",
};

type Field = { key: string; label: string; placeholder?: string; password?: boolean };

const FIELDS_BY_TYPE: Record<string, Field[]> = {
  WEBCHAT: [],
  WHATSAPP: [
    // Cloud API oficial (Meta for Developers) — número de teste gratuito
    { key: "phone_number_id", label: "Phone number ID (Meta for Developers)", placeholder: "ex.: 123456789012345" },
    { key: "access_token", label: "Access token (WhatsApp > API Setup)", password: true },
    { key: "app_secret", label: "App Secret (Configurações do app)", password: true },
  ],
  INSTAGRAM: [
    { key: "app_secret", label: "App Secret (app Meta)", password: true },
    { key: "access_token", label: "Access token da conta profissional", password: true },
  ],
  MESSENGER: [
    { key: "app_secret", label: "App Secret (app Meta)", password: true },
    { key: "access_token", label: "Access token da página", password: true },
  ],
  EMAIL: [
    { key: "imap_host", label: "Servidor IMAP", placeholder: "imap.provedor.com" },
    { key: "imap_port", label: "Porta IMAP", placeholder: "993" },
    { key: "imap_username", label: "Usuário IMAP", placeholder: "suporte@suaempresa.com" },
    { key: "imap_password", label: "Senha IMAP", password: true },
    { key: "smtp_host", label: "Servidor SMTP", placeholder: "smtp.provedor.com" },
    { key: "smtp_port", label: "Porta SMTP", placeholder: "587" },
    { key: "smtp_username", label: "Usuário SMTP", placeholder: "suporte@suaempresa.com" },
    { key: "smtp_password", label: "Senha SMTP", password: true },
    { key: "from_address", label: "Remetente (De:)", placeholder: "suporte@suaempresa.com" },
  ],
};

export default function ChannelsPage() {
  const queryClient = useQueryClient();
  const [type, setType] = useState<string>("WEBCHAT");
  const [name, setName] = useState("");
  const [credentials, setCredentials] = useState<Record<string, string>>({});
  // segredos exibidos UMA única vez, logo após conectar
  const [justConnected, setJustConnected] = useState<ConnectedChannel | null>(null);

  const { data: channels } = useQuery({ queryKey: ["channels"], queryFn: api.channels });

  const connectMutation = useMutation({
    mutationFn: () => api.connectChannel({ type, name, credentials }),
    onSuccess: (created) => {
      setName("");
      setCredentials({});
      setJustConnected(created.webhookPath ? created : null);
      queryClient.invalidateQueries({ queryKey: ["channels"] });
    },
  });

  const fields = FIELDS_BY_TYPE[type] ?? [];

  return (
    <div className="mx-auto max-w-3xl px-6 py-8">
      <h1 className="text-lg font-semibold">Canais</h1>
      <p className="mt-1 text-sm text-ink-soft">
        Todo canal conectado alimenta o mesmo inbox. Chat do site e e-mail funcionam na hora;
        WhatsApp, Instagram e Messenger pedem as credenciais do seu provedor.
      </p>

      <form
        onSubmit={(event) => {
          event.preventDefault();
          setJustConnected(null);
          connectMutation.mutate();
        }}
        className="mt-6 rounded-lg border border-line bg-paper-raised p-4"
      >
        <div className="flex flex-wrap items-end gap-3">
          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium">Tipo</span>
            <select
              value={type}
              onChange={(event) => {
                setType(event.target.value);
                setCredentials({});
              }}
              className="rounded-md border border-line-strong bg-paper-raised px-3 py-2 text-sm outline-none focus:border-accent"
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
              className="rounded-md border border-line-strong bg-paper-raised px-3 py-2 text-sm outline-none focus:border-accent focus:ring-2 focus:ring-accent-soft"
            />
          </label>
          <button
            type="submit"
            disabled={connectMutation.isPending}
            className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-accent-hover disabled:opacity-50"
          >
            {connectMutation.isPending ? "Conectando…" : "Conectar"}
          </button>
        </div>

        {fields.length > 0 && (
          <div className="mt-4 grid grid-cols-1 gap-3 border-t border-line pt-4 sm:grid-cols-3">
            {fields.map((field) => (
              <label key={field.key} className="flex flex-col gap-1.5">
                <span className="text-sm font-medium">{field.label}</span>
                <input
                  type={field.password ? "password" : "text"}
                  value={credentials[field.key] ?? ""}
                  onChange={(event) =>
                    setCredentials((current) => ({ ...current, [field.key]: event.target.value }))
                  }
                  placeholder={field.placeholder}
                  required
                  className="rounded-md border border-line-strong bg-paper-raised px-3 py-2 text-sm outline-none focus:border-accent focus:ring-2 focus:ring-accent-soft"
                />
              </label>
            ))}
            <p className="col-span-full text-xs text-ink-soft">
              Credenciais são criptografadas (AES-256) e nunca aparecem de volta na interface.
            </p>
          </div>
        )}
        {connectMutation.isError && (
          <p className="mt-3 text-sm text-danger">Falha ao conectar (apenas Admin pode conectar canais).</p>
        )}
      </form>

      {justConnected && <WebhookRevealCard channel={justConnected} onDismiss={() => setJustConnected(null)} />}

      <ul className="mt-6 flex flex-col gap-3">
        {channels?.map((channel) => <ChannelCard key={channel.id} channel={channel} />)}
        {channels?.length === 0 && (
          <li className="rounded-lg border border-dashed border-line-strong px-4 py-6 text-center text-sm text-ink-soft">
            Nenhum canal conectado ainda.
          </li>
        )}
      </ul>
    </div>
  );
}

/** Segredos de configuração do provedor — exibidos uma única vez. */
function WebhookRevealCard({ channel, onDismiss }: { channel: ConnectedChannel; onDismiss: () => void }) {
  const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
  const webhookUrl = `${apiUrl}${channel.webhookPath}`;

  return (
    <section className="mt-4 rounded-lg border border-accent bg-accent-soft p-4">
      <div className="flex items-start justify-between gap-3">
        <h2 className="text-sm font-semibold text-accent-ink">
          Configure no painel do provedor — estas informações aparecem só agora
        </h2>
        <button onClick={onDismiss} className="text-sm text-ink-soft hover:text-ink" aria-label="Fechar">
          ✕
        </button>
      </div>
      <div className="mt-3 flex flex-col gap-2.5">
        <CopyRow label="URL do webhook" value={webhookUrl} />
        {channel.verifyToken && <CopyRow label="Verify token (verificação da Meta)" value={channel.verifyToken} />}
      </div>
      <p className="mt-3 text-xs text-ink-soft">
        {channel.type === "WHATSAPP"
          ? "Meta for Developers → WhatsApp → Configuration → Webhook: cole a URL e o verify token acima; assine o campo \"messages\"."
          : "No app Meta: Webhooks → adicionar a URL acima e o verify token; assine o evento \"messages\"."}
      </p>
    </section>
  );
}

function CopyRow({ label, value }: { label: string; value: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <div>
      <p className="mb-1 text-xs font-medium text-ink-soft">{label}</p>
      <div className="flex items-center gap-2">
        <code className="min-w-0 flex-1 overflow-x-auto rounded-md border border-line bg-paper-raised px-2.5 py-1.5 font-mono text-xs whitespace-nowrap">
          {value}
        </code>
        <button
          onClick={() => {
            navigator.clipboard.writeText(value);
            setCopied(true);
            setTimeout(() => setCopied(false), 2000);
          }}
          className="shrink-0 rounded-md border border-line-strong bg-paper-raised px-2.5 py-1.5 text-xs font-medium transition-colors hover:border-accent hover:text-accent-ink"
        >
          {copied ? "Copiado!" : "Copiar"}
        </button>
      </div>
    </div>
  );
}

function ChannelCard({ channel }: { channel: Channel }) {
  const [copied, setCopied] = useState(false);
  const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
  const snippet = `<script src="${typeof window !== "undefined" ? window.location.origin : ""}/widget.js"\n        data-channel-id="${channel.id}"\n        data-api-url="${apiUrl}"></script>`;

  return (
    <li className="rounded-lg border border-line bg-paper-raised p-4">
      <div className="flex items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="font-medium">{channel.name}</p>
          <p className="truncate font-mono text-xs text-ink-soft">
            {TYPE_LABEL[channel.type] ?? channel.type} · {channel.id}
          </p>
        </div>
        <span
          className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-semibold ${
            channel.status === "ACTIVE"
              ? "bg-st-resolved-soft text-st-resolved"
              : "bg-paper-sunken text-ink-soft"
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
            <pre className="overflow-x-auto rounded-md bg-ink p-3 font-mono text-xs leading-relaxed text-paper">
              {snippet}
            </pre>
            <button
              onClick={() => {
                navigator.clipboard.writeText(snippet);
                setCopied(true);
                setTimeout(() => setCopied(false), 2000);
              }}
              className="absolute top-2 right-2 rounded bg-white/15 px-2 py-1 text-xs text-white transition-colors hover:bg-white/25"
            >
              {copied ? "Copiado!" : "Copiar"}
            </button>
          </div>
          <p className="mt-2 text-xs text-ink-soft">
            Testar agora:{" "}
            <a
              href={`/widget-demo.html?channelId=${channel.id}&apiUrl=${encodeURIComponent(apiUrl)}`}
              target="_blank"
              rel="noopener noreferrer"
              className="text-accent-ink underline underline-offset-2 hover:text-accent"
            >
              abrir página de demonstração
            </a>
          </p>
        </div>
      )}

      {(channel.type === "WHATSAPP" || channel.type === "INSTAGRAM" || channel.type === "MESSENGER") && (
        <p className="mt-2 text-xs text-ink-soft">
          A URL do webhook foi exibida na conexão do canal. Perdeu? Desabilite este canal e conecte um
          novo — um novo segredo será gerado.
        </p>
      )}
    </li>
  );
}
