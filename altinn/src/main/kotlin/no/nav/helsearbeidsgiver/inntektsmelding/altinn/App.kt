package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.altinn.CacheConfig
import no.nav.helsearbeidsgiver.maskinporten.MaskinportenClient
import no.nav.helsearbeidsgiver.maskinporten.MaskinportenClientConfig
import no.nav.helsearbeidsgiver.utils.log.logger
import kotlin.time.Duration.Companion.minutes

private val logger = "helsearbeidsgiver-im-altinn".logger()

fun main() {
    val maskinportenClient = createMaskinportenClient()
    val altinnClient = createAltinnClient(maskinportenClient)
    RapidApplication
        .create(System.getenv())
        .createAltinn(altinnClient)
        .start()
}

fun RapidsConnection.createAltinn(altinnClient: AltinnClient): RapidsConnection =
    also {
        logger.info("Starter ${TilgangLoeser::class.simpleName}...")
        TilgangLoeser(this, altinnClient)

        logger.info("Starter ${AltinnRiver::class.simpleName}...")
        AltinnRiver(altinnClient).connect(this)
    }

private fun createAltinnClient(maskinportenClient: MaskinportenClient): AltinnClient =
    AltinnClient(
        url = Env.url,
        serviceCode = Env.serviceCode,
        getToken = maskinportenClient::getToken,
        altinnApiKey = Env.altinnApiKey,
        cacheConfig = CacheConfig(60.minutes, 100),
    )

private fun createMaskinportenClient(): MaskinportenClient =
    MaskinportenClient(
        MaskinportenClientConfig(
            Env.Maskinporten.altinnScope,
            Env.Maskinporten.endpoint,
            Env.Maskinporten.clientJwk,
            Env.Maskinporten.issuer,
            Env.Maskinporten.clientId,
        ),
    )

private fun MaskinportenClient.getToken() =
    runBlocking {
        fetchNewAccessToken().tokenResponse.accessToken
    }
