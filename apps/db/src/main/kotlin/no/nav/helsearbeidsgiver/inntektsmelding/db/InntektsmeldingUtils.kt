package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding

fun SkjemaInntektsmelding.erDuplikatAv(other: SkjemaInntektsmelding): Boolean =
    this ==
        other.copy(
            avsenderTlf = avsenderTlf,
        )
