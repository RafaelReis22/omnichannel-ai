# Como testar (sem complicação)

Esquece a arquitetura por um minuto. O produto tem **duas telas**:

| Quem | Onde | O que faz |
|------|------|-----------|
| **Seu cliente** | o chat no site da loja (balão no canto) | pergunta "que horas abrem?", "qual o prazo de entrega?" |
| **Você (dono/atendente)** | o dashboard | vê todas as conversas, o que a IA respondeu sozinha, e os números |

A graça do produto: o cliente pergunta → **a IA responde sozinha** usando as
informações da loja → você só entra quando a IA não tem certeza. E tudo que a
IA decide fica registrado para você auditar (o "livro-razão").

## Rodar a demo (1 comando)

```bash
./demo.sh
```

Ele sobe tudo, cria uma loja de demonstração já com as informações
(horários, entrega, trocas, pagamento) e abre as duas telas no navegador.

## O que fazer depois que abrir

1. Na página do **site fictício**, clique no balão do chat e pergunte:
   > que horas vocês abrem no sábado?
2. Espere alguns segundos — a IA responde com o horário certo (ela leu isso
   da base de conhecimento, não é resposta decorada).
3. Agora abra o **dashboard** (login `demo@demo.local`, senha `demo-senha-1`):
   - **Inbox** → a conversa está lá, marcada "Com IA".
   - **IA & Livro-razão** → o registro da decisão: o que respondeu e com
     qual confiança. É isso que nenhum concorrente mostra.
   - **Relatórios** → os números do atendimento.
4. Volte no chat e pergunte algo que a loja NÃO sabe (ex.: "vocês vendem
   peças de trator?") — a IA vai passar para atendimento humano em vez de
   inventar. A conversa aparece no Inbox aguardando você.
5. Responda você mesmo pelo Inbox — pode anexar foto (📎), etiquetar,
   agendar visita ("Agendar") e criar negócio no pipeline ("+ Negócio").

## Se algo não funcionar

- IA não responde? O Ollama precisa estar instalado e aberto
  (`brew install ollama`). O `demo.sh` avisa se faltar.
- Nada abre? Docker Desktop precisa estar rodando.
- Qualquer outra coisa: os logs estão em `/tmp/omnichannel-backend.log` e
  `/tmp/omnichannel-frontend.log`.

## Isso vira produto de verdade como?

O mesmo dashboard, na internet (deploy), com o chat instalado no site real
do cliente (uma linha de código), WhatsApp/Instagram conectados (guia em
`docs/10-teste-sem-custo.md`) e a IA oficial (Claude) no lugar da local.
Nada do que você testou muda — só o endereço.
