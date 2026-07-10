// Auditoria visual + funcional: navega como um usuário e tira screenshots.
import { chromium } from "playwright";

const BASE = "http://localhost:3100";
const API = "http://localhost:8080";
const email = `audit-${Date.now()}@teste.com`;
const problems = [];

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
page.on("console", (msg) => {
  if (msg.type() === "error") problems.push(`console error em ${page.url()}: ${msg.text().slice(0, 200)}`);
});
page.on("pageerror", (err) => problems.push(`pageerror em ${page.url()}: ${String(err).slice(0, 200)}`));

async function shot(name) {
  await page.screenshot({ path: `/tmp/audit-${name}.png`, fullPage: false });
}

// 1. signup
await page.goto(`${BASE}/signup`);
await shot("01-signup");
await page.fill('input[type="text"] >> nth=0', "Auditoria Ltda");
await page.fill('input[type="text"] >> nth=1', "Rafael");
await page.fill('input[type="email"]', email);
await page.fill('input[type="password"]', "senha-audit-1");
await page.click('button[type="submit"]');
await page.waitForURL("**/inbox", { timeout: 15000 }).catch(() => problems.push("signup não redirecionou para /inbox"));
await shot("02-inbox-vazio");

// 2. canais: conectar webchat
await page.click('a[href="/canais"]');
await page.waitForTimeout(500);
await page.selectOption("select", "WEBCHAT");
await page.fill('input[placeholder*="Chat da loja"]', "Chat Auditoria");
await page.click('button:has-text("Conectar")');
await page.waitForTimeout(1200);
await shot("03-canais-webchat");
const snippet = await page.textContent("pre").catch(() => null);
if (!snippet?.includes("widget.js")) problems.push("snippet do widget não apareceu no card do canal");
const channelId = snippet?.match(/data-channel-id="([^"]+)"/)?.[1];

// 3. conectar whatsapp pela UI (como o usuário faria)
await page.selectOption("select", "WHATSAPP");
await page.fill('input[placeholder*="Chat da loja"]', "Zap Auditoria");
await page.fill('input[type="password"]', "chave-bsp-auditoria");
await page.click('button:has-text("Conectar")');
await page.waitForTimeout(1200);
await shot("04-canais-whatsapp");
const pageText = await page.textContent("body");
if (!pageText.includes("/api/whatsapp/webhook/")) problems.push("URL do webhook do WhatsApp não exibida após conectar");
if (!(await page.locator('button:has-text("Copiar")').count())) problems.push("botão Copiar do webhook ausente");

// 4. visitante manda mensagem via API pública (simula widget)
if (channelId) {
  const r = await fetch(`${API}/api/webchat/${channelId}/messages`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ visitorId: "vis-audit", visitorName: "Cliente Auditoria", text: "Olá! Vocês têm suporte por telefone?" }),
  });
  if (r.status !== 202) problems.push(`POST webchat retornou ${r.status}`);
}

// 5. inbox com conversa
await page.click('a[href="/inbox"]');
await page.waitForTimeout(3500); // materialização + ws/poll
await shot("05-inbox-conversa");
const listText = await page.textContent("body");
if (!listText.includes("suporte por telefone")) problems.push("conversa do visitante não apareceu no inbox em 3.5s");
if (!listText.includes("Cliente Auditoria")) problems.push("nome do contato NÃO aparece na lista de conversas");

// 6. abrir conversa, responder, nota
const item = page.locator("li button").first();
await item.click();
await page.waitForTimeout(800);
await shot("06-conversa-aberta");
await page.fill("textarea", "Temos sim! Seg a sex, 8h às 18h.");
await page.click('button:has-text("Enviar")');
await page.waitForTimeout(1000);
await page.click('button:has-text("Notas internas")').catch(() => problems.push("botão de notas internas não encontrado"));
await page.waitForTimeout(400);
await page.fill('input[placeholder*="nota interna"]', "Cliente perguntou de telefone — priorizar").catch(() => problems.push("campo de nota não encontrado"));
await page.click('button:has-text("Anotar")').catch(() => {});
await page.waitForTimeout(800);
await shot("07-conversa-nota");

// 7. busca
await page.fill('input[type="search"]', "telefone");
await page.waitForTimeout(900);
await shot("08-busca");
const searchText = await page.textContent("body");
if (!searchText.includes("Mensagem")) problems.push("busca por 'telefone' não retornou resultados visíveis");
await page.fill('input[type="search"]', "");

// 8. pipeline, conhecimento, ia
await page.click('a[href="/pipeline"]');
await page.waitForTimeout(800);
await shot("09-pipeline");
await page.click('a[href="/conhecimento"]');
await page.waitForTimeout(500);
await shot("10-conhecimento");
await page.click('a[href="/ia"]');
await page.waitForTimeout(800);
await shot("11-ia");

// 9. dark mode
await page.emulateMedia({ colorScheme: "dark" });
await page.click('a[href="/inbox"]');
await page.waitForTimeout(800);
await shot("12-inbox-dark");

console.log("PROBLEMAS ENCONTRADOS:");
problems.forEach((p) => console.log(" -", p));
if (problems.length === 0) console.log(" (nenhum detectado automaticamente)");
await browser.close();
