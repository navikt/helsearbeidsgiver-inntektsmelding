CREATE TABLE forespoersel_sak
(
    forespoersel_id UUID PRIMARY KEY,
    sak_id          TEXT UNIQUE NOT NULL,
    ferdigstilt     TIMESTAMP,
    slettes         TIMESTAMP   NOT NULL,
    opprettet       TIMESTAMP   NOT NULL DEFAULT now(),
    oppdatert       TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE TABLE forespoersel_oppgave
(
    forespoersel_id UUID PRIMARY KEY,
    oppgave_id      TEXT UNIQUE NOT NULL,
    ferdigstilt     TIMESTAMP,
    slettes         TIMESTAMP   NOT NULL,
    opprettet       TIMESTAMP   NOT NULL DEFAULT now(),
    oppdatert       TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE TABLE aapen_sak
(
    aapen_id  UUID PRIMARY KEY,
    sak_id    TEXT UNIQUE NOT NULL,
    slettes   TIMESTAMP   NOT NULL,
    opprettet TIMESTAMP   NOT NULL DEFAULT now(),
    oppdatert TIMESTAMP   NOT NULL DEFAULT now()
);
