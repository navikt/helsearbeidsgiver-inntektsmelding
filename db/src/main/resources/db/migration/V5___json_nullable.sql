ALTER TABLE inntektsmelding
    ALTER COLUMN dokument DROP NOT NULL;

ALTER TABLE inntektsmelding
    ADD COLUMN innsendt TIMESTAMP;