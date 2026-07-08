// Espelho manual do contrato OpenAPI do backend.
// TODO(fase-2): gerar via openapi-typescript a partir de /v3/api-docs.

export type ConversationStatus = "OPEN" | "WITH_AI" | "WITH_HUMAN" | "RESOLVED";
export type MessageSender = "CONTACT" | "AGENT" | "AI";
export type MessageKind = "TEXT" | "IMAGE" | "VIDEO" | "AUDIO" | "DOCUMENT";

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
}

export interface MfaChallenge {
  mfaRequired: true;
  mfaToken: string;
}

export interface Me {
  id: string;
  tenantId: string;
  name: string;
  email: string;
  role: "ADMIN" | "MANAGER" | "AGENT";
}

export interface Conversation {
  id: string;
  contactId: string;
  channelId: string;
  status: ConversationStatus;
  assigneeUserId: string | null;
  tags: string[];
  lastMessagePreview: string | null;
  lastMessageAt: string | null;
  createdAt: string;
}

export interface Message {
  id: string;
  sender: MessageSender;
  kind: MessageKind;
  text: string | null;
  mediaUrl: string | null;
  senderUserId: string | null;
  createdAt: string;
}

export interface Contact {
  id: string;
  name: string;
  tags: string[];
  identities: { channelType: string; externalId: string }[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export interface Channel {
  id: string;
  type: string;
  name: string;
  status: string;
  createdAt: string;
}

export interface KnowledgeDocument {
  id: string;
  title: string;
  createdAt: string;
}

export interface AiSettings {
  enabled: boolean;
  confidenceThreshold: number;
  tone: string | null;
}

export interface LedgerEntry {
  id: string;
  conversationId: string;
  action: "ANSWERED" | "HANDOFF";
  confidence: number;
  handoffSummary: string | null;
  reviewedByHuman: boolean;
  createdAt: string;
}
