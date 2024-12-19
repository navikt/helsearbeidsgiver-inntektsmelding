CREATE TABLE aapen_inntektsmelding
(
    id              BIGSERIAL NOT NULL PRIMARY KEY,
    aapen_id        UUID      NOT NULL,
    inntektsmelding JSONB     NOT NULL,
    journalpost_id  TEXT UNIQUE,
    opprettet       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX aapen_id_index ON aapen_inntektsmelding(aapen_id);
