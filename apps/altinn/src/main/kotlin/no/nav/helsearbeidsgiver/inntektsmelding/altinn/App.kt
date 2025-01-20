package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.altinn.Altinn3M2MClient
import no.nav.helsearbeidsgiver.altinn.CacheConfig
import no.nav.helsearbeidsgiver.maskinporten.MaskinportenClient
import no.nav.helsearbeidsgiver.maskinporten.MaskinportenClientConfig
import no.nav.helsearbeidsgiver.utils.log.logger
import kotlin.time.Duration.Companion.minutes

private val logger = "helsearbeidsgiver-im-altinn".logger()

fun main() {
    RapidApplication
        .create(System.getenv())
        .createAltinn(createAltinnClient())
        .start()
}

fun RapidsConnection.createAltinn(altinnClient: Altinn3M2MClient): RapidsConnection =
    also {
        logger.info("Starter ${TilgangRiver::class.simpleName}...")
        TilgangRiver(altinnClient).connect(this)

        logger.info("Starter ${AltinnRiver::class.simpleName}...")
        AltinnRiver(altinnClient).connect(this)
    }

private fun createAltinnClient(): Altinn3M2MClient {
    val maskinportenClient = createMaskinportenClient()
    return Altinn3M2MClient(
        baseUrl = Env.url,
        serviceCode = Env.serviceCode,
        getToken = maskinportenClient::getToken,
        cacheConfig = CacheConfig(60.minutes, 100),
    )
}

private fun createMaskinportenClient(): MaskinportenClient =
    MaskinportenClient(
        MaskinportenClientConfig(
            scope = Env.Maskinporten.altinnScope,
            endpoint = Env.Maskinporten.endpoint,
            clientJwk = Env.Maskinporten.clientJwk,
            issuer = Env.Maskinporten.issuer,
            clientId = Env.Maskinporten.clientId,
        ),
    )

private fun MaskinportenClient.getToken() =
    runBlocking {
        fetchNewAccessToken().tokenResponse.accessToken
    }
