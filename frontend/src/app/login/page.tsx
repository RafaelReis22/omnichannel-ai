"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { tokenStore } from "@/lib/auth";
import { AuthShell, Field, PrimaryButton } from "@/components/ui/auth-shell";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [mfaToken, setMfaToken] = useState<string | null>(null);
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      if (mfaToken) {
        const tokens = await api.verifyMfa({ mfaToken, code });
        tokenStore.save(tokens.accessToken, tokens.refreshToken);
        router.replace("/inbox");
        return;
      }
      const result = await api.login({ email, password });
      if ("mfaRequired" in result) {
        setMfaToken(result.mfaToken);
        return;
      }
      tokenStore.save(result.accessToken, result.refreshToken);
      router.replace("/inbox");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Falha ao entrar. Tente novamente.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell
      title={mfaToken ? "Verificação em duas etapas" : "Entrar"}
      subtitle={
        mfaToken
          ? "Digite o código do seu aplicativo autenticador ou um código de recuperação."
          : "Acesse o painel de atendimento."
      }
    >
      <form onSubmit={submit} className="flex flex-col gap-4">
        {mfaToken ? (
          <Field
            label="Código"
            type="text"
            value={code}
            onChange={setCode}
            placeholder="000000"
            autoFocus
          />
        ) : (
          <>
            <Field label="E-mail" type="email" value={email} onChange={setEmail} autoFocus />
            <Field label="Senha" type="password" value={password} onChange={setPassword} />
          </>
        )}
        {error && <p className="text-sm text-danger">{error}</p>}
        <PrimaryButton busy={busy}>{mfaToken ? "Verificar" : "Entrar"}</PrimaryButton>
      </form>
      {!mfaToken && (
        <p className="mt-6 text-sm text-ink-soft">
          Primeira vez aqui?{" "}
          <Link href="/signup" className="font-medium text-accent-ink underline underline-offset-2 hover:text-accent">
            Crie a conta da sua empresa
          </Link>
        </p>
      )}
    </AuthShell>
  );
}
