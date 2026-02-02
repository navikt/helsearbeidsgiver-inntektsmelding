package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.hag.simba.utils.felles.domene.SkjemaInntektsmeldingIntern as SkjemaInntektsmelding

fun SkjemaInntektsmelding.erDuplikatAv(other: SkjemaInntektsmelding): Boolean =
    this ==
        other.copy(
            avsenderTlf = avsenderTlf,
        )
