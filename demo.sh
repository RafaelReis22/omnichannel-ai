#!/bin/bash
# Demo guiada: sobe tudo, cria uma conta de demonstração com base de
# conhecimento e canal de chat, e te diz exatamente onde clicar.
# Uso: ./demo.sh   (requer: docker, java 21, node; ollama para a IA grátis)
set -e
cd "$(dirname "$0")"

API=http://localhost:8080
APP=http://localhost:3100
DEMO_EMAIL="demo@demo.local"
DEMO_SENHA="demo-senha-1"

bold() { printf "\033[1m%s\033[0m\n" "$1"; }
passo() { printf "\n\033[1;36m▶ %s\033[0m\n" "$1"; }

passo "1/5 Infra local (Postgres, Redis, RabbitMQ, MinIO)"
docker compose up -d --wait 2>/dev/null || docker compose up -d
sleep 2

passo "2/5 IA local (Ollama)"
if command -v ollama >/dev/null && curl -s --max-time 2 http://localhost:11434 >/dev/null; then
  ollama list | grep -q qwen2.5 || { echo "  baixando modelo de chat (uma vez, ~4.7GB)…"; ollama pull qwen2.5:7b; }
  ollama list | grep -q nomic-embed-text || { echo "  baixando modelo de embeddings (uma vez, ~274MB)…"; ollama pull nomic-embed-text; }
  echo "  ✓ IA local pronta (sem custo)"
else
  echo "  ⚠ Ollama não encontrado — instale com: brew install ollama  (e rode o app)"
  echo "    Sem ele a IA não responde; o resto da demo funciona."
fi

passo "3/5 Backend"
if [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 $API/actuator/health)" != "200" ]; then
  [ -f backend/target/backend-0.0.1-SNAPSHOT.jar ] || (cd backend && ./mvnw -q package -DskipTests)
  JWT_SECRET='demo-local-apenas-0123456789012345678901234567890123' \
  TOTP_ENCRYPTION_KEY='dGVzdGUtY2hhdmUtZGUtMzItYnl0ZXMtcGFyYS10c3Q=' \
  CHANNEL_CREDENTIALS_KEY='dGVzdGUtY2hhdmUtZGUtMzItYnl0ZXMtcGFyYS10c3Q=' \
  DATABASE_URL='jdbc:postgresql://localhost:5433/omnichannel' \
  DATABASE_PASSWORD='changeme-local-only' \
  REDIS_PORT=6380 RABBITMQ_PASSWORD='changeme-local-only' \
  AI_CHAT_PROVIDER=ollama AI_EMBEDDING_PROVIDER=ollama \
  CORS_ALLOWED_ORIGINS='http://localhost:3000,http://localhost:3100' \
  nohup java -jar backend/target/backend-0.0.1-SNAPSHOT.jar > /tmp/omnichannel-backend.log 2>&1 &
  printf "  aguardando backend"
  until [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 $API/actuator/health)" = "200" ]; do printf "."; sleep 2; done
  echo ""
fi
echo "  ✓ backend no ar ($API)"

passo "4/5 Dashboard"
if [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 $APP/login)" = "000" ]; then
  (cd frontend && [ -d node_modules ] || npm install --silent; PORT=3100 nohup npm run dev > /tmp/omnichannel-frontend.log 2>&1 &)
  printf "  aguardando dashboard"
  until curl -s -o /dev/null --max-time 2 $APP/login; do printf "."; sleep 2; done
  echo ""
fi
echo "  ✓ dashboard no ar ($APP)"

passo "5/5 Conta de demonstração"
TOKENS=$(curl -s -X POST $API/api/auth/signup -H 'Content-Type: application/json' \
  -d "{\"tenantName\":\"Minha Loja (demo)\",\"userName\":\"Você\",\"email\":\"$DEMO_EMAIL\",\"password\":\"$DEMO_SENHA\"}")
if ! echo "$TOKENS" | grep -q accessToken; then
  TOKENS=$(curl -s -X POST $API/api/auth/login -H 'Content-Type: application/json' \
    -d "{\"email\":\"$DEMO_EMAIL\",\"password\":\"$DEMO_SENHA\"}")
fi
TOKEN=$(echo "$TOKENS" | python3 -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")

# canal de chat (reusa se já existir)
CHANNEL=$(curl -s $API/api/channels -H "Authorization: Bearer $TOKEN" \
  | python3 -c "import sys,json;cs=[c for c in json.load(sys.stdin) if c['type']=='WEBCHAT'];print(cs[0]['id'] if cs else '')")
if [ -z "$CHANNEL" ]; then
  CHANNEL=$(curl -s -X POST $API/api/channels -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    -d '{"type":"WEBCHAT","name":"Chat do site","credentials":{}}' \
    | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
fi

# base de conhecimento (o que a IA sabe responder)
DOCS=$(curl -s $API/api/knowledge -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json;print(len(json.load(sys.stdin)))")
if [ "$DOCS" = "0" ]; then
  curl -s -o /dev/null -X POST $API/api/knowledge -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    -d '{"title":"Informações da loja","content":"Atendemos de segunda a sexta das 8h às 18h e aos sábados das 9h às 13h. Entregamos em toda a cidade em até 2 dias úteis; frete grátis acima de R$ 199. Trocas em até 30 dias com nota fiscal. Pagamos em pix, cartão em até 6x sem juros e boleto."}'
  echo "  ✓ base de conhecimento criada (horários, entrega, trocas, pagamento)"
fi
echo "  ✓ conta pronta"

bold "
══════════════════════════════════════════════════════════════════"
bold "  TUDO PRONTO — agora é só usar:"
echo "
  1) VOCÊ É O CLIENTE  →  $APP/widget-demo.html?channelId=$CHANNEL
     Clique no balão no canto e pergunte algo tipo:
     \"que horas vocês abrem no sábado?\" ou \"qual o prazo de entrega?\"
     A IA responde sozinha em alguns segundos.

  2) VOCÊ É O DONO DA LOJA  →  $APP
     Login: $DEMO_EMAIL    Senha: $DEMO_SENHA
     Veja a conversa no Inbox, a decisão da IA em \"IA & Livro-razão\"
     e os números em \"Relatórios\".
"
if [ "$(uname)" = "Darwin" ]; then
  open "$APP/widget-demo.html?channelId=$CHANNEL"
  open "$APP"
fi
