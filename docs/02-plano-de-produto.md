# Plano de Produto

## MVP

Objetivo do MVP: provar que uma empresa consegue atender clientes por WhatsApp + webchat + e-mail em um único painel, com uma IA que resolve de verdade (com RAG e handoff confiável), e que o modelo de preço não assusta ninguém — no menor tempo possível de construção.

### Dentro do MVP

**Canais**
- WhatsApp Business (via BSP — ver [stack tecnológica](04-stack-tecnologica.md)).
- Chat do site (widget próprio).
- E-mail (caixa compartilhada, IMAP/SMTP + provedor transacional).

**Inbox unificado**
- Painel único de conversas com histórico completo por contato, independente do canal.
- Etiquetas, filtros básicos (canal, status, responsável), busca textual simples.
- Atribuição manual de conversas a agentes.

**IA**
- Resposta automática com contexto de conversa (memória de curto prazo) + base de conhecimento (RAG) sobre documentos enviados pela empresa.
- Envio de imagens, PDFs e documentos pela IA quando solicitados pelo cliente (upload feito pela empresa na base de conhecimento).
- Handoff automático para humano quando a IA não atinge confiança mínima configurável, ou quando o cliente pede explicitamente.
- Resumo automático de conversa ao transferir para humano.
- Livro-razão de resolução (versão inicial): cada resposta autônoma marcada com confiança e link para a transcrição.

**Multiempresa e administração**
- Multi-tenant desde o dia 1 (isolamento por `tenant_id` + Row-Level Security no banco).
- Login com e-mail/senha + convite de usuários por e-mail.
- Papéis básicos: Admin, Agente, Gestor (permissões por papel, não granular ainda).
- Configuração de canais (conectar WhatsApp/e-mail/webchat) e configuração básica da IA (tom de voz, base de conhecimento, limiar de confiança) na própria interface.
- Log de auditoria básico (quem conectou canal, quem mudou configuração de IA, quem excluiu dado).

**CRM simples**
- Cadastro de contato criado automaticamente na primeira mensagem.
- Pipeline de vendas com estágios configuráveis (kanban simples).
- Tags em contatos e conversas.

**Preço**
- Plano único de lançamento: assento fixo + pacote de resoluções de IA incluído + overage a taxa fixa e publicada, simulador de fatura simples no dashboard.

### Fora do MVP (fast-follow explícito)

| Item | Por que fica de fora | Fase alvo |
|---|---|---|
| Instagram e Messenger | Exige integração adicional com Meta Graph API além do WhatsApp; não é o canal primário do público-alvo inicial | Fase 2 |
| Telegram | Canal explicitamente "futuro" no escopo original | Fase 4 |
| Agendamento de reuniões/visitas integrado a calendário | Depende de integrações externas (Google/Outlook Calendar) que não bloqueiam a prova de valor central | Fase 2 |
| Qualificação de lead e detecção de intenção de compra via IA dedicada | Requer o pipeline de CRM e histórico já maduros para treinar/calibrar critérios | Fase 3 |
| Sugestões de venda para vendedores (copiloto) | Depende de volume de conversas históricas para ser útil | Fase 3 |
| Permissões granulares (por canal, por funcionalidade) | RBAC simples do MVP é suficiente para os primeiros clientes | Fase 4 |
| API pública e webhooks para terceiros | Só faz sentido com clientes que já pedem integração própria | Fase 4 |
| Analytics avançado (funis, cohort, previsão) | MVP tem analytics operacional básico, não estratégico | Fase 4 |
| Envio de vídeo pela IA | Tecnicamente trivial, mas de baixa prioridade sem casos de uso validados no MVP | Fase 2 |
| Voz (IA por chamada) | Categoria ainda nascente no mercado; diferencial de fases posteriores | Fase 5 |
| Seleção de LLM exposta ao cliente / multi-LLM com failover | Diferencial de posicionamento, não bloqueador de valor inicial | Fase 5 |

## Backlog (por épico)

### Épico: Canais
- Conectar WhatsApp Business via BSP (onboarding guiado, QR/token).
- Widget de chat embutível para o site do cliente.
- Ingestão de e-mail (IMAP/SMTP + provedor transacional) como canal de conversa.
- Conector Instagram Direct (Meta Graph API).
- Conector Facebook Messenger (Meta Graph API).
- Conector Telegram (Bot API).
- Health-check e reconexão automática de canais (alertar quando um canal cai).

### Épico: Inbox e Conversas
- Painel único com lista de conversas, filtros e busca.
- Histórico consolidado por contato entre canais.
- Etiquetas customizáveis.
- Atribuição manual e automática (round-robin, por carga) de conversas.
- Busca global (conversas, contatos, mensagens, documentos da base de conhecimento).
- Notas internas em conversas (não visíveis ao cliente).

### Épico: IA e Automação
- Motor de resposta com RAG sobre base de conhecimento (upload de documentos, indexação, embeddings).
- Memória de conversa (curto prazo por sessão + long-term por contato).
- Envio de mídia pela IA (imagem, PDF, documento, vídeo).
- Portão de confiança configurável por tipo de ação (responder FAQ vs. agendar vs. dar desconto).
- Handoff automático para humano com resumo da conversa.
- Resumo de conversa sob demanda.
- Qualificação de lead (score baseado em intenção detectada).
- Detecção de intenção de compra.
- Agendamento de reuniões (integração com calendário).
- Registro de visita/agendamento de visita (vertical imobiliária/varejo físico).
- Sugestões de resposta para vendedores (copiloto).
- Livro-razão de resolução (dashboard de auditoria de decisões da IA).
- Simulador de fatura em tempo real.
- Configuração de tom de voz e política de autonomia da IA.

### Épico: CRM e Vendas
- Cadastro e enriquecimento automático de contato.
- Pipeline de vendas configurável (estágios, motivos de perda).
- Campos customizados em contato/negócio.
- Importação/exportação de contatos.

### Épico: Multiempresa e Administração
- Cadastro de empresa (tenant) e onboarding inicial.
- Convite e gestão de usuários.
- Papéis e permissões (RBAC simples no MVP, granular na Fase 4).
- Configuração de canais por empresa.
- Log de auditoria de ações administrativas e de IA.
- Logs operacionais consultáveis (erros de integração, falhas de envio).

### Épico: Plataforma e Integrações
- API pública (REST) para contatos, conversas e mensagens.
- Webhooks de eventos (nova conversa, handoff, negócio movido de estágio).
- Chaves de API por tenant com escopos.
- Painel de analytics (volume de conversas, tempo de resposta, taxa de resolução por canal).

## Roadmap

| Fase | Objetivo | Principais entregas |
|---|---|---|
| **Fase 0 — Fundação** | Infraestrutura pronta para receber o MVP | Multi-tenant + auth, schema de banco inicial, CI/CD, observabilidade básica, adapter de canal (interface comum) |
| **Fase 1 — MVP** | Provar valor com um cliente piloto real | WhatsApp + webchat + e-mail, inbox unificado, IA com RAG e handoff, CRM simples, preço previsível, livro-razão v1 |
| **Fase 2 — Omnichannel e produtividade** | Expandir canais e reduzir trabalho manual do time | Instagram, Messenger, envio de vídeo, agendamento de reuniões/visitas, busca global, notas internas |
| **Fase 3 — IA comercial** | IA vira ferramenta de vendas, não só suporte | Qualificação de lead, detecção de intenção de compra, sugestões para vendedores, analytics operacional mais rico |
| **Fase 4 — Enterprise-ready (sem enterprise-only)** | Atender mid-market sem virar Ada/Sierra | Permissões granulares, API pública, webhooks, auditoria completa, Telegram, analytics avançado |
| **Fase 5 — Diferenciação de categoria** | Consolidar o posicionamento único no mercado | Voz com paridade de qualidade ao chat, multi-LLM exposto ao cliente, SLA contratual de acurácia, pacote de compliance (SOC2/LGPD avançado) |

Critério de saída de cada fase: não é data — é adoção real pelo cliente piloto/base inicial das funcionalidades da fase anterior antes de começar a próxima.
