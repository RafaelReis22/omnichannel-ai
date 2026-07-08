"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { tokenStore } from "@/lib/auth";
import { AuthShell, Field, PrimaryButton } from "@/components/ui/auth-shell";

export default function SignupPage() {
  const router = useRouter();
  const [tenantName, setTenantName] = useState("");
  const [userName, setUserName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const tokens = await api.signup({ tenantName, userName, email, password });
      tokenStore.save(tokens.accessToken, tokens.refreshToken);
      router.replace("/inbox");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Falha ao criar a conta.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell title="Criar conta" subtitle="Sua empresa e seu usuário administrador, em um passo.">
      <form onSubmit={submit} className="flex flex-col gap-4">
        <Field label="Nome da empresa" type="text" value={tenantName} onChange={setTenantName} autoFocus />
        <Field label="Seu nome" type="text" value={userName} onChange={setUserName} />
        <Field label="E-mail" type="email" value={email} onChange={setEmail} />
        <Field label="Senha (mínimo 8 caracteres)" type="password" value={password} onChange={setPassword} />
        {error && <p className="text-sm text-danger">{error}</p>}
        <PrimaryButton busy={busy}>Criar conta</PrimaryButton>
      </form>
      <p className="mt-6 text-sm text-ink-soft">
        Já tem conta?{" "}
        <Link href="/login" className="font-medium text-accent-ink underline underline-offset-2 hover:text-accent">
          Entrar
        </Link>
      </p>
    </AuthShell>
  );
}
