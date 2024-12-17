DROP TABLE inntektsmelding;

CREATE TABLE forespoersel
(
    forespoersel_id      VARCHAR(40) PRIMARY KEY,
    opprettet TIMESTAMP   NOT NULL DEFAULT now(),
    sak_id VARCHAR(36),
    oppgave_id VARCHAR(36),
    orgnr VARCHAR(12)
);


CREATE TABLE inntektsmelding
(
    id        BIGSERIAL   NOT NULL PRIMARY KEY,
    dokument  TEXT        ,
    forespoersel_id      VARCHAR(40) NOT NULL,
    journalpostid VARCHAR(30),
    innsendt TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT fk_forespoersel
        FOREIGN KEY(forespoersel_id)
            REFERENCES forespoersel(forespoersel_id)
);