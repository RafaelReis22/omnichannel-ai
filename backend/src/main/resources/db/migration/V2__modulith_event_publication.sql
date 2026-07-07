-- Registro de publicação de eventos do Spring Modulith (padrão Outbox —
-- docs/03-arquitetura.md): eventos de domínio são persistidos na mesma
-- transação do dado de negócio e reentregues se um consumidor falhar.
-- Schema oficial: https://docs.spring.io/spring-modulith/reference/appendix.html
CREATE TABLE event_publication (
    id UUID NOT NULL,
    listener_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    serialized_event TEXT NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);

CREATE INDEX event_publication_serialized_event_hash_idx
    ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx
    ON event_publication (completion_date);
