package no.nav.helsearbeidsgiver.inntektsmelding.brreg

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
internal val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-brreg")

fun main() {
    val env = System.getenv()
    RapidApplication.create(env).apply {

    }.start()
}


fun main2() {
    logger.info("Henter inn environment...")
    sikkerlogg.info("Henter inn environment...")
    val app = createApp(setUpEnvironment())
    app.start().also {
        sikkerlogg.info("Første melding på Rapid fra brreg modulen...")
        logger.info("Første melding på Rapid fra brreg modulen...")
        app.publish("Første melding på Rapid fra brreg modulen")
    }
}

fun createApp(environment: Environment): RapidsConnection {
    val rapidsConnection = RapidApplication.create(environment.raw)
    BrregLøser(rapidsConnection)
    return rapidsConnection
}
