package no.nav.helsearbeidsgiver.felles.utils

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode

fun List<Periode>.tilString(): String =
    if (size < 2) {
        "${first().fom.tilNorskFormat()} - ${first().tom.tilNorskFormat()}"
    } else {
        "${first().fom.tilNorskFormat()} - [...] - ${last().tom.tilNorskFormat()}"
    }
