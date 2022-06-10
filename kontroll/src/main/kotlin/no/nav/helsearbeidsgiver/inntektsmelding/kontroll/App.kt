package no.nav.helsearbeidsgiver.inntektsmelding.kontroll

import no.nav.helse.rapids_rivers.RapidApplication
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
internal val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-kontroll")

fun main() {
    val env = System.getenv()
    RapidApplication.create(env).apply {
        KontrollLøser(this)
    }.start()

}
