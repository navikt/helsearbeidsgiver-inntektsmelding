package no.nav.helsearbeidsgiver.inntektsmelding.aareg

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.felles.auth.AuthClient
import no.nav.helsearbeidsgiver.felles.auth.IdentityProvider
import no.nav.helsearbeidsgiver.utils.cache.LocalCache
import no.nav.helsearbeidsgiver.utils.log.logger
import kotlin.time.Duration.Companion.minutes

private val logger = "im-aareg".logger()

fun main() {
    RapidApplication
        .create(System.getenv())
        .createAaregRiver(buildClient())
        .start()
}

fun RapidsConnection.createAaregRiver(aaregClient: AaregClient): RapidsConnection =
    also {
        logger.info("Starter ${HentAnsettelsesperioderRiver::class.simpleName}...")
        HentAnsettelsesperioderRiver(aaregClient).connect(this)
    }

private fun buildClient(): AaregClient {
    val tokenGetter = AuthClient().tokenGetter(IdentityProvider.AZURE_AD, Env.aaregScope)
    return AaregClient(
        url = Env.aaregUrl,
        cacheConfig = LocalCache.Config(5.minutes, 1000),
        getAccessToken = tokenGetter,
    )
}
