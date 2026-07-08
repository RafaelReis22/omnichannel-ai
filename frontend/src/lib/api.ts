import { tokenStore } from "./auth";
import type {
  AiSettings,
  Board,
  DealCard,
  Channel,
  Contact,
  Conversation,
  KnowledgeDocument,
  LedgerEntry,
  Me,
  Message,
  MfaChallenge,
  Page,
  TokenResponse,
} from "./types";

const BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  constructor(
    public status: number,
    detail: string,
  ) {
    super(detail);
  }
}

let refreshing: Promise<boolean> | null = null;

async function tryRefresh(): Promise<boolean> {
  // uma única renovação em voo, mesmo com várias queries em 401 ao mesmo tempo
  refreshing ??= (async () => {
    const refreshToken = tokenStore.refresh();
    if (!refreshToken) return false;
    const response = await fetch(`${BASE_URL}/api/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
    if (!response.ok) {
      tokenStore.clear();
      return false;
    }
    const tokens = (await response.json()) as TokenResponse;
    tokenStore.save(tokens.accessToken, tokens.refreshToken);
    return true;
  })().finally(() => {
    refreshing = null;
  });
  return refreshing;
}

async function request<T>(path: string, init?: RequestInit, retried = false): Promise<T> {
  const headers = new Headers(init?.headers);
  headers.set("Content-Type", "application/json");
  const token = tokenStore.access();
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const response = await fetch(`${BASE_URL}${path}`, { ...init, headers });

  if (response.status === 401 && !retried && tokenStore.refresh()) {
    if (await tryRefresh()) return request<T>(path, init, true);
    window.location.assign("/login");
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new ApiError(response.status, body?.detail ?? `Erro ${response.status}`);
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export const api = {
  // --- auth ---
  signup: (body: { tenantName: string; userName: string; email: string; password: string }) =>
    request<TokenResponse>("/api/auth/signup", { method: "POST", body: JSON.stringify(body) }),
  login: (body: { email: string; password: string }) =>
    request<TokenResponse | MfaChallenge>("/api/auth/login", { method: "POST", body: JSON.stringify(body) }),
  verifyMfa: (body: { mfaToken: string; code: string }) =>
    request<TokenResponse>("/api/auth/2fa/verify", { method: "POST", body: JSON.stringify(body) }),
  me: () => request<Me>("/api/me"),

  // --- inbox ---
  conversations: (params: { status?: string; page?: number }) => {
    const query = new URLSearchParams();
    if (params.status) query.set("status", params.status);
    if (params.page) query.set("page", String(params.page));
    return request<Page<Conversation>>(`/api/conversations?${query}`);
  },
  conversation: (id: string) => request<Conversation>(`/api/conversations/${id}`),
  messages: (conversationId: string) =>
    request<Page<Message>>(`/api/conversations/${conversationId}/messages?size=100`),
  reply: (conversationId: string, text: string) =>
    request<Message>(`/api/conversations/${conversationId}/reply`, {
      method: "POST",
      body: JSON.stringify({ kind: "TEXT", text }),
    }),
  assign: (conversationId: string, userId: string) =>
    request<Conversation>(`/api/conversations/${conversationId}/assign`, {
      method: "POST",
      body: JSON.stringify({ userId }),
    }),
  resolve: (conversationId: string) =>
    request<Conversation>(`/api/conversations/${conversationId}/resolve`, { method: "POST" }),
  contact: (id: string) => request<Contact>(`/api/contacts/${id}`),

  // --- canais ---
  channels: () => request<Channel[]>("/api/channels"),
  connectChannel: (body: { type: string; name: string; credentials: Record<string, string> }) =>
    request<Channel>("/api/channels", { method: "POST", body: JSON.stringify(body) }),

  // --- IA ---
  knowledge: () => request<KnowledgeDocument[]>("/api/knowledge"),
  addKnowledge: (body: { title: string; content: string }) =>
    request<KnowledgeDocument>("/api/knowledge", { method: "POST", body: JSON.stringify(body) }),
  deleteKnowledge: (id: string) => request<void>(`/api/knowledge/${id}`, { method: "DELETE" }),
  aiSettings: () => request<AiSettings>("/api/ai/settings"),
  updateAiSettings: (body: AiSettings) =>
    request<AiSettings>("/api/ai/settings", { method: "PUT", body: JSON.stringify(body) }),
  ledger: (page = 0) => request<Page<LedgerEntry>>(`/api/ai/ledger?page=${page}`),
  reviewLedger: (id: string) => request<LedgerEntry>(`/api/ai/ledger/${id}/review`, { method: "POST" }),

  // --- CRM ---
  board: () => request<Board>("/api/crm/board"),
  createDeal: (body: { contactId: string; title: string; value?: number | null; stageId?: string }) =>
    request<DealCard>("/api/crm/deals", { method: "POST", body: JSON.stringify(body) }),
  moveDeal: (dealId: string, stageId: string) =>
    request<DealCard>(`/api/crm/deals/${dealId}/move`, { method: "POST", body: JSON.stringify({ stageId }) }),
  deleteDeal: (dealId: string) => request<void>(`/api/crm/deals/${dealId}`, { method: "DELETE" }),
};
