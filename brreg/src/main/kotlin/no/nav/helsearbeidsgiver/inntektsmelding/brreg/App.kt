package no.nav.helsearbeidsgiver.inntektsmelding.brreg

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-brreg".logger()

fun main() {
    val brregClient = BrregClient(Env.brregUrl)
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
        logger.info("Starter ${VirksomhetLoeser::class.simpleName}... developmentMode: $isDevelopmentMode")
        VirksomhetLoeser(this, brregClient, isDevelopmentMode)

        logger.info("Starter ${HentVirksomhetNavnRiver::class.simpleName}... (isDevelopmentMode: $isDevelopmentMode)")
        HentVirksomhetNavnRiver(brregClient, isDevelopmentMode).connect(this)
    }
