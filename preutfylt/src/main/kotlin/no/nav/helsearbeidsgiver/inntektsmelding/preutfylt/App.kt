package no.nav.helsearbeidsgiver.inntektsmelding.preutfylt

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.log.loggerSikker

val sikkerlogg = loggerSikker()

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
