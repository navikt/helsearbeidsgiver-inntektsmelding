package no.nav.helsearbeidsgiver.inntektsmelding.api.utils

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode

fun Periode.hardcodedJson(): String =
    """
    {
        "fom": "$fom",
        "tom": "$tom"
    }
    """

fun <T : Any> T?.jsonStrOrNull(): String? = this?.let { "\"$it\"" }
