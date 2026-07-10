"use client";

/** Moldura das telas de autenticação: painel de marca + cartão do formulário. */
export function AuthShell({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle: string;
  children: React.ReactNode;
}) {
  return (
    <main className="grid min-h-dvh lg:grid-cols-[2fr_3fr]">
      <aside className="hidden flex-col justify-between border-r border-line bg-paper-sunken p-10 lg:flex">
        <p className="text-sm font-semibold tracking-tight">
          omnichannel<span className="text-accent">·</span>ai
        </p>
        <div>
          <h2 className="max-w-md text-3xl font-semibold leading-tight text-balance">
            Todos os canais em um painel. IA que mostra o que resolve.
          </h2>
          <p className="mt-4 max-w-sm text-ink-soft">
            WhatsApp, Instagram, e-mail e chat do site com respostas automáticas auditáveis no
            livro-razão de resolução.
          </p>
        </div>
        <p className="text-xs text-ink-soft">© 2026</p>
      </aside>
      <section className="flex items-center justify-center p-6">
        <div className="w-full max-w-sm">
          <h1 className="text-2xl font-semibold">{title}</h1>
          <p className="mt-1 mb-8 text-sm text-ink-soft">{subtitle}</p>
          {children}
        </div>
      </section>
    </main>
  );
}

export function Field({
  label,
  type,
  value,
  onChange,
  placeholder,
  autoFocus,
}: {
  label: string;
  type: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  autoFocus?: boolean;
}) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="text-sm font-medium">{label}</span>
      <input
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        autoFocus={autoFocus}
        required
        className="rounded-md border border-line-strong bg-paper-raised px-3 py-2 text-ink outline-none transition-colors focus:border-accent focus:ring-2 focus:ring-accent-soft"
      />
    </label>
  );
}

export function PrimaryButton({ busy, children }: { busy?: boolean; children: React.ReactNode }) {
  return (
    <button
      type="submit"
      disabled={busy}
      className="mt-2 rounded-md bg-accent px-4 py-2.5 font-medium text-white transition-colors hover:bg-accent-hover disabled:opacity-50"
    >
      {busy ? "Aguarde…" : children}
    </button>
  );
}
