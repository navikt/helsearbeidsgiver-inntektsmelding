ALTER TABLE inntektsmelding
    ALTER COLUMN dokument TYPE JSONB USING dokument::JSONB;
