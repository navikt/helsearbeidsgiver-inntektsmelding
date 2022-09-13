package no.nav.helsearbeidsgiver.inntektsmelding.akkumulator

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerlogg: Logger = LoggerFactory.getLogger("tjenestekall")
internal val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-akkumulator")

fun main() {
    val environment = setUpEnvironment()

    val redisClient = RedisStore(environment.redisUrl)

    RapidApplication.create(environment.raw).apply {
        Akkumulator(this, redisClient)
    }.apply {
        register(object : RapidsConnection.StatusListener {
            override fun onShutdown(rapidsConnection: RapidsConnection) {
                redisClient.shutdown()
            }
        })
    }.start()
}
