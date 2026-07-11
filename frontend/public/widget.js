/**
 * omnichannel-ai — widget de webchat embeddável.
 *
 * Uso no site do cliente:
 *   <script src="https://SEU_DASHBOARD/widget.js"
 *           data-channel-id="UUID_DO_CANAL"
 *           data-api-url="https://SUA_API"></script>
 *
 * Vanilla JS de arquivo único, sem dependências. Shadow DOM isola os
 * estilos do site hospedeiro. Fala apenas com a API pública de webchat
 * (o channelId é a site key; o tenant nunca vem do cliente).
 */
(function () {
  "use strict";

  var script = document.currentScript;
  if (!script) return;
  var CHANNEL_ID = script.getAttribute("data-channel-id");
  var API_URL = (script.getAttribute("data-api-url") || "http://localhost:8080").replace(/\/$/, "");
  if (!CHANNEL_ID) {
    console.warn("[omnichannel-ai] data-channel-id ausente no script do widget.");
    return;
  }

  var POLL_MS = 3500;
  var STORAGE_KEY = "ocw.visitor." + CHANNEL_ID;

  var visitorId = localStorage.getItem(STORAGE_KEY);
  if (!visitorId) {
    visitorId = "vis-" + (crypto.randomUUID ? crypto.randomUUID() : Date.now() + "-" + Math.random().toString(36).slice(2));
    localStorage.setItem(STORAGE_KEY, visitorId);
  }

  var open = false;
  var unread = 0;
  var rendered = {}; // id -> true (dedup de render entre polls)
  var pendingEchoes = []; // bolhas otimistas aguardando a versão real do poll
  var pollTimer = null;

  // ---------- DOM (Shadow) ----------
  var host = document.createElement("div");
  host.style.cssText = "position:fixed;bottom:20px;right:20px;z-index:2147483000;";
  var shadow = host.attachShadow({ mode: "closed" });
  shadow.innerHTML =
    '<style>' +
    ':host{all:initial}' +
    '*{box-sizing:border-box;font-family:ui-sans-serif,system-ui,-apple-system,sans-serif}' +
    '.btn{width:56px;height:56px;border-radius:50%;border:none;cursor:pointer;background:#0f766e;color:#fff;' +
    'box-shadow:0 4px 16px rgba(0,0,0,.25);display:flex;align-items:center;justify-content:center;position:relative;' +
    'transition:transform .15s ease}' +
    '.btn:hover{transform:scale(1.06)}' +
    '.btn svg{width:26px;height:26px}' +
    '.badge{position:absolute;top:-2px;right:-2px;min-width:18px;height:18px;border-radius:9px;background:#dc2626;' +
    'color:#fff;font-size:11px;font-weight:700;display:flex;align-items:center;justify-content:center;padding:0 5px}' +
    '.panel{display:none;flex-direction:column;width:min(360px,calc(100vw - 40px));height:min(520px,calc(100vh - 120px));' +
    'background:#fff;border-radius:14px;box-shadow:0 12px 40px rgba(0,0,0,.28);overflow:hidden;margin-bottom:12px}' +
    '.panel.open{display:flex}' +
    '.head{background:#0f766e;color:#fff;padding:14px 16px;display:flex;justify-content:space-between;align-items:center}' +
    '.head b{font-size:15px}' +
    '.head small{display:block;opacity:.8;font-size:12px;font-weight:400;margin-top:2px}' +
    '.close{background:none;border:none;color:#fff;font-size:20px;cursor:pointer;line-height:1;padding:4px}' +
    '.thread{flex:1;overflow-y:auto;padding:14px;background:#f6f5f2;display:flex;flex-direction:column;gap:8px}' +
    '.msg{max-width:80%;padding:8px 12px;border-radius:12px;font-size:14px;line-height:1.45;white-space:pre-wrap;word-break:break-word}' +
    '.msg.contact{align-self:flex-end;background:#0f766e;color:#fff;border-bottom-right-radius:4px}' +
    '.msg.agent{align-self:flex-start;background:#fff;color:#1f2430;border:1px solid #e6e3dc;border-bottom-left-radius:4px}' +
    '.msg.ai{align-self:flex-start;background:#f3ecfd;color:#1f2430;border:1px solid #d9c7f5;border-bottom-left-radius:4px}' +
    '.msg .who{font-size:10px;font-weight:700;opacity:.65;margin-bottom:2px;text-transform:uppercase;letter-spacing:.04em}' +
    '.compose{display:flex;gap:8px;padding:10px;border-top:1px solid #e6e3dc;background:#fff}' +
    '.compose textarea{flex:1;resize:none;border:1px solid #d8d5cd;border-radius:8px;padding:8px 10px;font-size:14px;' +
    'outline:none;max-height:90px}' +
    '.compose textarea:focus{border-color:#0f766e}' +
    '.compose button{border:none;background:#0f766e;color:#fff;border-radius:8px;padding:0 14px;font-size:14px;' +
    'font-weight:600;cursor:pointer}' +
    '.compose button:disabled{opacity:.5;cursor:default}' +
    '.hint{text-align:center;font-size:10px;color:#8a877f;padding:4px 0 8px;background:#fff}' +
    '@media (prefers-color-scheme: dark){' +
    '.panel{background:#1b1e26}.thread{background:#14161c}' +
    '.msg.agent{background:#242833;color:#e8e6df;border-color:#343947}' +
    '.msg.ai{background:#2a2438;color:#e8e6df;border-color:#4a3d63}' +
    '.compose{background:#1b1e26;border-color:#343947}' +
    '.compose textarea{background:#242833;color:#e8e6df;border-color:#3a3f4d}' +
    '.hint{background:#1b1e26;color:#6d7280}}' +
    '</style>' +
    '<div class="panel" part="panel">' +
    '  <div class="head"><div><b>Atendimento</b><small>Normalmente respondemos em minutos</small></div>' +
    '  <button class="close" aria-label="Fechar">&times;</button></div>' +
    '  <div class="thread" role="log" aria-live="polite"></div>' +
    '  <form class="compose"><textarea rows="1" placeholder="Escreva sua mensagem…" aria-label="Mensagem"></textarea>' +
    '  <button type="submit">Enviar</button></form>' +
    '  <div class="hint">omnichannel-ai</div>' +
    '</div>' +
    '<button class="btn" aria-label="Abrir chat" aria-expanded="false">' +
    '  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 11.5a8.38 8.38 0 0 1-8.5 8.5c-1.6 0-3.1-.4-4.4-1.2L3 20l1.2-5.1A8.38 8.38 0 0 1 12.5 3a8.5 8.5 0 0 1 8.5 8.5z"/></svg>' +
    '</button>';

  var panel = shadow.querySelector(".panel");
  var button = shadow.querySelector(".btn");
  var thread = shadow.querySelector(".thread");
  var form = shadow.querySelector(".compose");
  var textarea = shadow.querySelector("textarea");
  var sendBtn = form.querySelector("button");

  function setBadge() {
    var existing = button.querySelector(".badge");
    if (existing) existing.remove();
    if (unread > 0) {
      var badge = document.createElement("span");
      badge.className = "badge";
      badge.textContent = unread > 9 ? "9+" : String(unread);
      button.appendChild(badge);
    }
  }

  function render(message) {
    if (rendered[message.id]) return false;
    rendered[message.id] = true;
    // a versão real de uma mensagem do visitante substitui o eco otimista
    if (message.sender === "CONTACT" && !String(message.id).startsWith("local-") && pendingEchoes.length > 0) {
      var echo = pendingEchoes.shift();
      if (echo && echo.parentNode) echo.remove();
    }
    var div = document.createElement("div");
    var kind = message.sender === "CONTACT" ? "contact" : message.sender === "AI" ? "ai" : "agent";
    div.className = "msg " + kind;
    if (kind !== "contact") {
      var who = document.createElement("div");
      who.className = "who";
      who.textContent = "Atendente"; // cliente não distingue IA de humano
      div.appendChild(who);
    }
    if (message.kind === "IMAGE" && message.mediaUrl) {
      var img = document.createElement("img");
      img.src = message.mediaUrl;
      img.alt = "Imagem";
      img.style.cssText = "max-width:100%;max-height:200px;border-radius:8px;display:block";
      div.appendChild(img);
    } else if (message.kind && message.kind !== "TEXT" && message.mediaUrl) {
      var link = document.createElement("a");
      link.href = message.mediaUrl;
      link.target = "_blank";
      link.rel = "noopener noreferrer";
      link.textContent = "Abrir anexo (" + message.kind.toLowerCase() + ")";
      div.appendChild(link);
    }
    if (message.text || message.kind === "TEXT" || !message.mediaUrl) {
      var text = document.createElement("div");
      text.textContent = message.text || (message.kind === "TEXT" ? "" : "[" + (message.kind || "").toLowerCase() + "]");
      if (text.textContent) div.appendChild(text);
    }
    thread.appendChild(div);
    if (String(message.id).startsWith("local-")) {
      pendingEchoes.push(div);
    }
    thread.scrollTop = thread.scrollHeight;
    return true;
  }

  function poll() {
    fetch(API_URL + "/api/webchat/" + CHANNEL_ID + "/messages?visitorId=" + encodeURIComponent(visitorId))
      .then(function (response) { return response.ok ? response.json() : []; })
      .then(function (messages) {
        (messages || []).forEach(function (message) {
          var isNew = render(message);
          if (isNew && !open && message.sender !== "CONTACT") {
            unread += 1;
            setBadge();
          }
        });
      })
      .catch(function () { /* rede indisponível: o próximo poll tenta de novo */ });
  }

  function startPolling() {
    if (pollTimer) return;
    poll();
    pollTimer = setInterval(poll, POLL_MS);
  }

  function toggle(openNow) {
    open = openNow;
    panel.classList.toggle("open", open);
    button.setAttribute("aria-expanded", String(open));
    if (open) {
      unread = 0;
      setBadge();
      startPolling();
      textarea.focus();
    }
  }

  button.addEventListener("click", function () { toggle(!open); });
  shadow.querySelector(".close").addEventListener("click", function () { toggle(false); });

  form.addEventListener("submit", function (event) {
    event.preventDefault();
    var text = textarea.value.trim();
    if (!text) return;
    sendBtn.disabled = true;
    var clientMessageId = crypto.randomUUID ? crypto.randomUUID() : String(Date.now());
    fetch(API_URL + "/api/webchat/" + CHANNEL_ID + "/messages", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ visitorId: visitorId, text: text, clientMessageId: clientMessageId }),
    })
      .then(function (response) {
        if (response.status === 202) {
          textarea.value = "";
          // eco otimista; o poll substituiria, mas o id real difere — marca
          // um placeholder local que o dedup de render ignora
          render({ id: "local-" + clientMessageId, sender: "CONTACT", text: text });
        }
      })
      .catch(function () { /* mantém o texto para o visitante reenviar */ })
      .finally(function () { sendBtn.disabled = false; });
  });

  textarea.addEventListener("keydown", function (event) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      form.requestSubmit();
    }
  });

  document.body.appendChild(host);
})();
