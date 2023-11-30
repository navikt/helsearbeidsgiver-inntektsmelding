package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.altinn.CacheConfig
import no.nav.helsearbeidsgiver.utils.log.logger
import kotlin.time.Duration.Companion.minutes

private val logger = "helsearbeidsgiver-im-altinn".logger()

fun main() {
    RapidApplication
        .create(System.getenv())
        .createAltinn(createAltinnClient())
        .start()
}

fun RapidsConnection.createAltinn(altinnClient: AltinnClient): RapidsConnection =
    also {
        logger.info("Starter ${TilgangLoeser::class.simpleName}...")
        TilgangLoeser(this, altinnClient)
    }

private fun createAltinnClient(): AltinnClient =
    AltinnClient(
        url = Env.url,
        serviceCode = Env.serviceCode,
        apiGwApiKey = Env.apiGwApiKey,
        altinnApiKey = Env.altinnApiKey,
        cacheConfig = CacheConfig(60.minutes, 100)
    )
