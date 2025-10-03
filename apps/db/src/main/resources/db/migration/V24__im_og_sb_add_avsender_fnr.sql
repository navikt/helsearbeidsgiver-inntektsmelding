ALTER TABLE inntektsmelding
    ADD COLUMN avsender_fnr VARCHAR(11);

ALTER TABLE selvbestemt_inntektsmelding
    ADD COLUMN avsender_fnr VARCHAR(11);
