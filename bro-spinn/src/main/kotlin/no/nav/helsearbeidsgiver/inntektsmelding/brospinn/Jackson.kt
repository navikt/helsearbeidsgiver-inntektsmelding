package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import no.nav.helsearbeidsgiver.felles.json.jacksonOm
import no.nav.inntektsmeldingkontrakt.Inntektsmelding

object Jackson {
    fun fromJson(json: String): Inntektsmelding =
        jacksonOm.readValue(json, Inntektsmelding::class.java)

    fun toJson(im: Inntektsmelding): String =
        jacksonOm.writeValueAsString(im)
}
