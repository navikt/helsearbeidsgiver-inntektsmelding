package no.nav.helsearbeidsgiver.inntektsmelding.brreg

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.utils.cache.LocalCache
import no.nav.helsearbeidsgiver.utils.log.logger
import kotlin.time.Duration.Companion.days

private val logger = "im-brreg".logger()

fun main() {
    val brregClient =
        BrregClient(
            url = Env.brregUrl,
            cacheConfig = LocalCache.Config(7.days, 10_000),
        )
    val isDevelopmentMode = Env.brregUrl.contains("localhost")

    RapidApplication
        .create(System.getenv())
        .createBrregRiver(brregClient, isDevelopmentMode)
        .start()
}

fun RapidsConnection.createBrregRiver(
    brregClient: BrregClient,
    isDevelopmentMode: Boolean,
): RapidsConnection =
    also {
        logger.info("Starter ${HentOrganisasjonNavnRiver::class.simpleName}... (isDevelopmentMode: $isDevelopmentMode)")
        HentOrganisasjonNavnRiver(brregClient, isDevelopmentMode).connect(this)
    }
