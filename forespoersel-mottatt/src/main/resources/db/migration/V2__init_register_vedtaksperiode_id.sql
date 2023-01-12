CREATE TABLE register_vedtaksperiode_id
(
    id                BIGSERIAL NOT NULL PRIMARY KEY,
    forespoersel_id   UUID      NOT NULL DEFAULT gen_random_uuid(),
    vedtaksperiode_id UUID      NOT NULL,
    opprettet         TIMESTAMP NOT NULL DEFAULT now(),
    oppdatert         TIMESTAMP NOT NULL DEFAULT now()
);
