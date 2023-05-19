package no.nav.helsearbeidsgiver.inntektsmelding.preutfylt

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

val sikkerlogg = sikkerLogger()

fun main() {
    RapidApplication
        .create(System.getenv())
        .createPreutfylt()
        .start()
}

fun RapidsConnection.createPreutfylt(): RapidsConnection {
    HentPreutfyltLøser(this)
    return this
}
