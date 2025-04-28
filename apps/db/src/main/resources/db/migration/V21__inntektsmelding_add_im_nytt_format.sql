ALTER TABLE inntektsmelding
    ADD COLUMN inntektsmelding JSONB,
    ADD COLUMN erProsessert    BOOLEAN NOT NULL DEFAULT FALSE;
