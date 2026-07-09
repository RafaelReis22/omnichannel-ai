"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useSyncExternalStore } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { tokenStore } from "@/lib/auth";

const noopSubscribe = () => () => {};

const NAV = [
  { href: "/inbox", label: "Inbox" },
  { href: "/pipeline", label: "Pipeline" },
  { href: "/canais", label: "Canais" },
  { href: "/conhecimento", label: "Conhecimento" },
  { href: "/ia", label: "IA & Livro-razão" },
] as const;

export default function AppLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  // client-only (localStorage): false no SSR, valor real após hidratar
  const isLoggedIn = useSyncExternalStore(noopSubscribe, () => tokenStore.isLoggedIn(), () => false);

  useEffect(() => {
    if (!isLoggedIn) router.replace("/login");
  }, [isLoggedIn, router]);

  const { data: me } = useQuery({ queryKey: ["me"], queryFn: api.me, enabled: isLoggedIn });

  if (!isLoggedIn) return null;

  return (
    <div className="grid min-h-dvh grid-cols-[13.5rem_1fr]">
      <aside className="flex flex-col bg-panel text-panel-ink">
        <p className="px-5 pt-6 pb-8 font-mono text-sm tracking-wide text-panel-muted">
          omnichannel-ai
        </p>
        <nav aria-label="Navegação principal" className="flex flex-col gap-0.5 px-3">
          {NAV.map((item) => {
            const active = pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`rounded-md px-3 py-2 text-sm font-medium transition-colors ${
                  active
                    ? "bg-white/10 text-panel-ink"
                    : "text-panel-muted hover:bg-white/5 hover:text-panel-ink"
                }`}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>
        <div className="mt-auto border-t border-white/10 px-5 py-4">
          {me && (
            <p className="truncate text-sm">
              {me.name}
              <span className="block truncate text-xs text-panel-muted">{me.email}</span>
            </p>
          )}
          <button
            onClick={() => {
              tokenStore.clear();
              router.replace("/login");
            }}
            className="mt-2 text-xs text-panel-muted underline underline-offset-2 hover:text-panel-ink"
          >
            Sair
          </button>
        </div>
      </aside>
      <main className="min-w-0">{children}</main>
    </div>
  );
}
