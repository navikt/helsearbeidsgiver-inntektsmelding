ALTER TABLE inntektsmelding
    ALTER COLUMN forespoersel_id TYPE UUID USING forespoersel_id::UUID,
    ALTER COLUMN journalpostid TYPE TEXT USING journalpostid::TEXT;

ALTER TABLE inntektsmelding
    RENAME COLUMN journalpostid TO journalpost_id;
