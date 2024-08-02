package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding

fun Inntektsmelding.erDuplikatAv(other: Inntektsmelding): Boolean =
    this ==
        other.copy(
            vedtaksperiodeId = vedtaksperiodeId,
            tidspunkt = tidspunkt,
            årsakInnsending = årsakInnsending,
            innsenderNavn = innsenderNavn,
        )

fun Innsending.erDuplikatAv(other: Innsending): Boolean =
    this ==
        other.copy(
            årsakInnsending = årsakInnsending,
        )
