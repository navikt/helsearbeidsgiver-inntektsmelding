package no.nav.helsearbeidsgiver.inntektsmelding.akkumulator

import no.nav.helse.rapids_rivers.RapidApplication
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
internal val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-akkumulator")

fun main() {
    val environment = setUpEnvironment()

    RapidApplication.create(environment.raw).apply {
        Akkumulator(this, environment.redisUrl)
    }.start()
}
