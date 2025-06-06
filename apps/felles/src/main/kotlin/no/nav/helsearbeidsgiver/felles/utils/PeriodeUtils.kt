package no.nav.helsearbeidsgiver.felles.utils

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.utils.date.tilNorskFormat

fun List<Periode>.tilKortFormat(): String =
    if (size < 2) {
        "${first().fom.tilNorskFormat()} - ${first().tom.tilNorskFormat()}"
    } else {
        "${first().fom.tilNorskFormat()} - [...] - ${last().tom.tilNorskFormat()}"
    }
