"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useSyncExternalStore } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { tokenStore } from "@/lib/auth";
import { useRealtime } from "@/lib/use-realtime";

const noopSubscribe = () => () => {};

const NAV = [
  { href: "/inbox", label: "Inbox" },
  { href: "/agenda", label: "Agenda" },
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
  useRealtime(isLoggedIn);

  if (!isLoggedIn) return null;

  return (
    <div className="grid min-h-dvh grid-cols-[13rem_1fr]">
      <aside className="flex flex-col border-r border-line bg-paper-sunken">
        <p className="px-5 pt-5 pb-6 text-sm font-semibold tracking-tight">
          omnichannel<span className="text-accent">·</span>ai
        </p>
        <nav aria-label="Navegação principal" className="flex flex-col gap-0.5 px-3">
          {NAV.map((item) => {
            const active = pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`rounded-md border-l-2 px-3 py-1.5 text-sm transition-colors ${
                  active
                    ? "border-l-accent bg-paper-raised font-semibold text-ink shadow-sm"
                    : "border-l-transparent font-medium text-ink-soft hover:bg-paper-raised hover:text-ink"
                }`}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>
        <div className="mt-auto border-t border-line px-5 py-4">
          {me && (
            <p className="truncate text-sm font-medium">
              {me.name}
              <span className="block truncate text-xs font-normal text-ink-soft">{me.email}</span>
            </p>
          )}
          <button
            onClick={() => {
              tokenStore.clear();
              router.replace("/login");
            }}
            className="mt-2 text-xs text-ink-soft underline underline-offset-2 hover:text-ink"
          >
            Sair
          </button>
        </div>
      </aside>
      <main className="min-w-0 bg-paper">{children}</main>
    </div>
  );
}
