package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.altinn.Altinn3M2MClient
import no.nav.helsearbeidsgiver.altinn.CacheConfig
import no.nav.helsearbeidsgiver.felles.auth.AuthClient
import no.nav.helsearbeidsgiver.felles.auth.IdentityProvider
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

private fun createAltinnClient(): Altinn3M2MClient =
    Altinn3M2MClient(
        baseUrl = Env.altinnTilgangerBaseUrl,
        serviceCode = Env.serviceCode,
        getToken = AuthClient().tokenGetter(IdentityProvider.AZURE_AD, Env.altinnScope),
        cacheConfig = CacheConfig(60.minutes, 5000),
    )
