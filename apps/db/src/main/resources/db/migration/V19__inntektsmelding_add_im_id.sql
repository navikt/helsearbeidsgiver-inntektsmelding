ALTER TABLE inntektsmelding
    ADD COLUMN inntektsmelding_id UUID UNIQUE,
    ADD COLUMN avsender_navn      TEXT;
