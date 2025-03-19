package no.nav.helsearbeidsgiver.felles.utils

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding

// midlertidlig duplisering for å støtte flere endringsÅrsaker
fun SkjemaInntektsmelding.konverterEndringAarsakTilListe(): SkjemaInntektsmelding =
    if (this.inntekt?.endringAarsaker.isNullOrEmpty()) {
        this.copy(
            inntekt =
                this.inntekt?.copy(
                    endringAarsaker = listOfNotNull(this.inntekt?.endringAarsak),
                ),
        )
    } else {
        this
    }
