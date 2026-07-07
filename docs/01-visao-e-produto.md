# Visão e Produto

## Missão

Dar a empresas de pequeno e médio porte acesso a atendimento com IA agêntica de qualidade enterprise — sem exigir contratos enterprise, sem esconder como a IA realmente performa, e sem prender o cliente a uma plataforma de dados paralela para funcionar.

## Visão

Ser a plataforma de atendimento omnichannel em que qualquer empresa consegue ver, com números auditáveis, o que a IA está realmente resolvendo — e nunca é surpreendida pela fatura.

Isso nasce de um padrão encontrado em todos os 20 concorrentes pesquisados (ver `../research` do projeto de estratégia): a distância entre a taxa de resolução anunciada em marketing e a taxa real reportada em uso é sistematicamente de 20 a 40 pontos percentuais, e o modelo de cobrança (por resolução, crédito, sessão ou MAC) quase sempre gera faturas imprevisíveis. Não é uma falha pontual de um fornecedor — é estrutural ao setor, porque nenhum incumbente pode resolver isso sem canibalizar sua própria receita de overage e add-ons.

## Público-alvo

### Perfil primário (foco do MVP e da Fase 1–2)
- Empresas de 10 a 200 funcionários, times de atendimento/vendas de 3 a 40 pessoas.
- Já usam WhatsApp como canal principal de contato com o cliente (Brasil e LatAm em geral).
- Hoje usam uma combinação frágil de WhatsApp Business App + planilha + um helpdesk fraco (Zenvia, Octadesk, JivoChat) ou nenhuma ferramenta de IA real.
- Verticais iniciais: e-commerce/D2C, serviços com agendamento (clínicas, imobiliárias, educação), e vendas B2B de ciclo curto.

### Perfil secundário (Fase 3–4, expansão)
- Mid-market (200–1000 funcionários) que hoje está preso entre helpdesks SMB fracos e soluções enterprise-only (Ada, Decagon, Sierra) que exigem contratos de US$70–350 mil/ano sem self-serve.
- Empresas migrando de fornecedores em consolidação (ex.: clientes órfãos do Drift, descontinuado pela Salesloft em mar/2026).

### Fora de escopo (por ora)
- Enterprise com exigência de SSO/HIPAA/SOC2 imediato — vira alvo só na Fase 4+.
- Empresas fora de canais de mensageria (só telefone/e-mail tradicional) — não é o wedge inicial.

## Diferenciais

| Diferencial | Lacuna de mercado que ele fecha |
|---|---|
| **Livro-razão de resolução** — cada resolução autônoma é registrada com transcrição e score de confiança, auditável pelo cliente | Nenhum concorrente publica metodologia de como mede "resolução" |
| **Preço híbrido e previsível** (assento + pacote incluso + overage a taxa fixa publicada) | Zendesk cobrou overage sem aviso (jan/2026); Fin/Intercom teve faturas saltando 5–10x |
| **Zero pré-requisito de plataforma paralela** | Salesforce exige Data Cloud (US$108 mil+/ano); Kustomer exige migração completa |
| **Self-serve com caminho para enterprise, sem trocar de produto** | Ada/Decagon/Sierra são enterprise-only, sem self-serve |
| **WhatsApp como canal de primeira classe desde o dia 1** | A maioria dos concorrentes globais trata WhatsApp como integração secundária |
| **Cérebro de conhecimento único entre canais e funcionalidades de IA** | Help Scout mantém bases separadas entre AI Answers e AI Drafts |
| **Autonomia com portão de confiança configurável por tipo de ação** | Reduz o gap entre resolução anunciada e real ao não deixar a IA agir sem confiança suficiente |

## Princípios de produto

1. **Transparência é feature, não disclaimer.** Toda métrica de IA exposta ao cliente deve ser auditável, não apenas exibida.
2. **Nunca surpreender a fatura.** Qualquer cobrança variável tem teto visível e simulador antes de acontecer.
3. **Omnichannel de verdade, não integração cosmética.** Um agente humano ou a IA devem ver o histórico completo do contato independente do canal de origem.
4. **A IA erra — o produto deve tornar o erro barato.** Handoff para humano é sempre uma opção de primeira classe, não um fallback envergonhado.
