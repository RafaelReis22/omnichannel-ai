# Estratégia de Escalabilidade

## Princípio

Escalar por decoupling antes de escalar por força bruta. O monólito modular (ver [arquitetura](03-arquitetura.md)) já separa Channel Gateway, Core e AI Worker por fila — isso permite escalar cada um horizontalmente de forma independente antes de qualquer extração em serviço separado.

## Eixos de escala e gatilhos

| Eixo | Sintoma que dispara a ação | Ação |
|---|---|---|
| API stateless | Latência de resposta subindo sob carga concorrente | Múltiplas réplicas atrás de load balancer — trivial, pois a API não guarda estado em memória |
| AI Worker | Fila de processamento de IA acumulando (lag crescente) | Escalar número de workers independente da API; é o componente com maior custo de CPU/latência (chamadas a LLM) |
| Channel Gateway | Rate limit de um canal específico (ex.: WhatsApp) sendo atingido | Isolar filas por canal, para que um canal congestionado não atrase os demais |
| Banco de dados | Leituras de dashboard/analytics competindo com escrita transacional | Réplica de leitura para analytics e busca, mantendo a réplica primária só para escrita transacional |
| Multi-tenancy | Um tenant grande consumindo recursos de forma desproporcional aos demais (ruído entre inquilinos) | Rate limiting por tenant na API e nas filas; avaliação de schema/DB dedicado para contas enterprise que exigirem isolamento físico |
| Busca vetorial (RAG) | Latência de busca em `pgvector` crescendo com o volume de documentos | Migrar para um vector store dedicado (Qdrant/Weaviate) mantendo a mesma interface de repositório — troca de implementação, não de contrato |
| Busca textual global | Volume de conversas tornando `tsvector` lento | Migrar para Meilisearch, alimentado pelos mesmos eventos de domínio já publicados internamente |

## Caminho de evolução da arquitetura

1. **Hoje → MVP**: monólito modular, um banco Postgres, filas no mesmo Redis, tudo em poucas réplicas.
2. **Tração inicial**: separar o AI Worker em processo/deploy independente (primeiro serviço extraído, porque é o gargalo mais provável).
3. **Múltiplos tenants grandes**: introduzir réplica de leitura no Postgres; avaliar partição de tenants de maior volume.
4. **Escala enterprise**: extrair Channel Gateway como serviço próprio por canal se um canal específico dominar o volume; considerar fila mais robusta (Kafka) só se BullMQ/Redis se mostrar insuficiente — não antecipar essa troca sem evidência de gargalo real.

## O que deliberadamente não se otimiza cedo

- Sharding de banco por tenant — YAGNI até haver um tenant que realmente demande isolamento físico (por contrato ou volume).
- Multi-região — não é requisito do público-alvo inicial (Brasil/LatAm); revisitar se/quando houver expansão geográfica real.
- Arquitetura de microsserviços completa — o custo de coordenação superaria o benefício até existirem sinais concretos de gargalo por módulo.
