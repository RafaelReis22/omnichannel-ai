# Estratégia de Segurança

## Isolamento multi-tenant

- Toda tabela de negócio carrega `tenant_id`; Row-Level Security no Postgres impõe o isolamento no próprio banco, não apenas na camada de aplicação — uma falha de lógica na API não deve conseguir vazar dado entre tenants.
- Chaves de API e webhooks são escopadas por tenant e nunca concedem acesso cross-tenant.

## Autenticação e autorização

- Autenticação via Spring Security com JWT (access curto + refresh com rotação), senhas com hash Argon2id, suporte a 2FA desde o MVP para papel Admin.
- RBAC via CASL: papéis (Admin, Gestor, Agente) no MVP, evoluindo para permissões granulares por recurso/canal na Fase 4.
- Toda ação sensível (mudar configuração de IA, conectar/desconectar canal, exportar dados, alterar permissão) passa por checagem de autorização no backend — nunca confiar em ocultar botão no frontend como controle de acesso.

## Proteção de dados e segredos

- Segredos (tokens de canal, chaves de API de LLM, credenciais de e-mail) nunca em código-fonte; variáveis de ambiente em desenvolvimento, gerenciador de segredos (ex.: Doppler ou AWS Secrets Manager) em produção.
- Credenciais de canal armazenadas criptografadas em repouso (não em texto plano no banco).
- Criptografia em trânsito (TLS) em toda comunicação externa e interna entre serviços.
- Verificação de assinatura de webhook em todo evento recebido de canais externos (Meta, provedor de e-mail) antes de processar.

## LGPD e privacidade

- Dados pessoais de contatos (telefone, e-mail, conteúdo de conversa) tratados como dado sensível por padrão: minimização de coleta, finalidade explícita, e mecanismo de exclusão de dados de um contato mediante solicitação.
- Direito de exportação e exclusão de dados do tenant (portabilidade) disponível desde o MVP, mesmo que via processo manual assistido inicialmente — automatizar quando o volume de solicitações justificar.
- Log de auditoria não armazena o conteúdo integral de mensagens do cliente além do necessário para rastreabilidade de ações administrativas.

## Segurança de aplicação web

- CSP (Content Security Policy) com nonce por requisição no dashboard, sem `unsafe-inline` para scripts.
- Sanitização de qualquer conteúdo renderizado que venha de terceiros (mensagens de clientes, respostas de IA) antes de exibir como HTML — nunca `dangerouslySetInnerHTML` sem sanitização.
- CSRF habilitado em toda rota que muda estado; rate limiting por IP e por tenant nas rotas de autenticação e na API pública.
- Cabeçalhos de segurança padrão (HSTS, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`) em todas as respostas.

## Auditoria e resposta a incidentes

- Log de auditoria imutável (somente inserção) para: login, mudança de permissão, conexão/desconexão de canal, mudança de configuração de IA, exportação/exclusão de dados.
- Logs operacionais (erros de integração, falhas de webhook) retidos por período definido e correlacionáveis por `tenant_id` e `request_id`.
- Processo de rotação de segredos documentado, incluindo o que fazer se uma credencial de canal for exposta (revogar, reconectar, notificar tenant afetado).

## Roadmap de compliance

| Fase | Nível de compliance |
|---|---|
| MVP | LGPD básico (minimização, exportação/exclusão sob pedido), 2FA para Admin, RLS multi-tenant |
| Fase 4 | Auditoria completa, permissões granulares, prontidão para questionário de segurança de cliente mid-market |
| Fase 5 | Pacote de compliance avançado (SOC2 Type I/II, opção de residência de dados na UE) como funcionalidade premium — ver [plano de produto](02-plano-de-produto.md) |
