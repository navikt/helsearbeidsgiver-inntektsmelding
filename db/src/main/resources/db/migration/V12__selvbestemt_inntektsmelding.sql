DROP TABLE aapen_inntektsmelding;

CREATE TABLE selvbestemt_inntektsmelding
(
    id                 BIGSERIAL PRIMARY KEY,
    inntektsmelding_id UUID UNIQUE NOT NULL,
    selvbestemt_id     UUID        NOT NULL,
    inntektsmelding    JSONB       NOT NULL,
    journalpost_id     TEXT UNIQUE,
    opprettet          TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX selvbestemt_id_index ON selvbestemt_inntektsmelding (selvbestemt_id);
