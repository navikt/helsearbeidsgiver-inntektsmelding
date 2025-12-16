-- Kopier naturalytelser fra inntekt til rot-nivå, eller opprett tom liste dersom inntekt mangler
-- Gjør dette bare hvis ikke nytt felt allerede er satt!
UPDATE inntektsmelding
    SET skjema = jsonb_set(
            skjema,
            '{naturalytelser}',
            COALESCE(
                    skjema->'inntekt'->'naturalytelser',
                    '[]'::jsonb
            )
     )
WHERE skjema IS NOT NULL
    and skjema -> 'naturalytelser' is null;

UPDATE inntektsmelding
SET inntektsmelding = jsonb_set(
        inntektsmelding,
        '{naturalytelser}',
        COALESCE(
                inntektsmelding->'inntekt'->'naturalytelser',
                '[]'::jsonb
        )
             )
WHERE inntektsmelding IS NOT NULL
  and inntektsmelding -> 'naturalytelser' is null;

UPDATE selvbestemt_inntektsmelding
SET inntektsmelding = jsonb_set(
        inntektsmelding,
        '{naturalytelser}',
        COALESCE(
                inntektsmelding->'inntekt'->'naturalytelser',
                '[]'::jsonb
        )
                      )
WHERE inntektsmelding IS NOT NULL
  and inntektsmelding -> 'naturalytelser' is null;
