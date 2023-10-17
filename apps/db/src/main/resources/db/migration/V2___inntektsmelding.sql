CREATE TABLE inntektsmelding
(
    id        BIGSERIAL   NOT NULL PRIMARY KEY,
    dokument  TEXT        NOT NULL,
    uuid      VARCHAR(40) NOT NULL,
    opprettet TIMESTAMP   NOT NULL DEFAULT now()
);
