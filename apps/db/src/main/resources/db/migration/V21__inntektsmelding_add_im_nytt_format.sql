ALTER TABLE inntektsmelding
    ADD COLUMN inntektsmelding JSONB,
    ADD COLUMN prosessert      TIMESTAMP;
