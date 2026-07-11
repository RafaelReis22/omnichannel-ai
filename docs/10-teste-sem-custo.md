# 10 — Testar tudo sem gastar nada

Guia para rodar e testar a plataforma inteira **sem nenhuma conta paga**:
canais pelo Meta for Developers (número de teste gratuito), IA por modelo
local (Ollama) e armazenamento por MinIO local.

## 1. Infra local (grátis)

```bash
docker compose up -d          # Postgres, Redis, RabbitMQ, MinIO
```

## 2. IA local com Ollama (sem chave da Anthropic/OpenAI)

O backend troca de provedor por variável de ambiente — nenhuma mudança de
código:

```bash
# uma vez: instalar e baixar os modelos (https://ollama.com)
brew install ollama
ollama pull qwen2.5:7b        # chat (bom em português; ~5 GB de RAM)
ollama pull nomic-embed-text  # embeddings do RAG

# ao subir o backend
export AI_CHAT_PROVIDER=ollama
export AI_EMBEDDING_PROVIDER=ollama
```

Notas:
- Máquina com pouca RAM: `export OLLAMA_CHAT_MODEL=llama3.2:3b` (mais leve,
  qualidade menor).
- Os embeddings locais têm 768 dimensões e a coluna do vector store tem
  1536 — o backend completa com zeros automaticamente (não altera o ranking
  do retrieval). **Não misture** provedores de embedding na mesma base: ao
  trocar OpenAI↔Ollama, reindexe a base de conhecimento (recrie os
  documentos) — vetores de modelos diferentes não são comparáveis.
- Qualidade: modelos locais pequenos erram mais que o Claude — o portão de
  confiança e o fail-open para humano continuam valendo, então o
  comportamento do produto segue o mesmo. Para demo/venda, use as chaves
  reais.

## 3. WhatsApp de verdade com o número de teste da Meta (grátis)

1. [developers.facebook.com](https://developers.facebook.com) → criar app →
   adicionar produto **WhatsApp**. A Meta dá um número de teste gratuito
   (até 5 destinatários verificados).
2. Em **WhatsApp → API Setup**, copie o **Phone number ID** e o **access
   token** temporário; em **Configurações do app → Básico**, o **App
   Secret**.
3. No dashboard (Canais → WhatsApp), cole os três — a tela devolve a **URL
   do webhook** e o **verify token** (aparecem uma única vez).
4. O webhook precisa de URL pública. Túnel gratuito:
   ```bash
   brew install cloudflared
   cloudflared tunnel --url http://localhost:8080
   ```
   Use a URL gerada + o caminho do webhook no painel da Meta
   (**WhatsApp → Configuration → Webhook**), com o verify token; assine o
   campo `messages`.
5. Mande mensagem do seu celular (destinatário verificado) para o número de
   teste — ela cai no inbox; resposta do agente (texto e mídia) volta pelo
   número de teste.

Instagram/Messenger seguem o mesmo fluxo (app Meta + página/conta de teste,
webhook único `/api/meta/webhook/{channelId}`).

## 4. E-mail e webchat

- **Webchat**: 100% nosso — funciona local sem conta nenhuma
  (`/widget-demo.html`).
- **E-mail**: qualquer conta IMAP/SMTP gratuita (Gmail com senha de app,
  por exemplo) nos campos do canal.

## 5. Subindo o backend em modo grátis

```bash
cd backend && ./mvnw package -DskipTests
AI_CHAT_PROVIDER=ollama AI_EMBEDDING_PROVIDER=ollama \
JWT_SECRET=... TOTP_ENCRYPTION_KEY=... CHANNEL_CREDENTIALS_KEY=... \
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

Com o provedor em `ollama`, `ANTHROPIC_API_KEY`/`OPENAI_API_KEY` não são
necessárias — os starters pagos ficam desligados.
