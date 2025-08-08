package no.nav.helsearbeidsgiver.inntektsmelding.brreg

import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.felles.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.utils.cache.LocalCache
import kotlin.time.Duration.Companion.days

fun main() {
    val brregClient =
        BrregClient(
            url = Env.brregUrl,
            cacheConfig = LocalCache.Config(7.days, 10_000),
        )
    val isDevelopmentMode = Env.brregUrl.contains("localhost")

    ObjectRiver.connectToRapid {
        createHentOrganisasjonNavnRiver(brregClient, isDevelopmentMode)
    }
}

fun createHentOrganisasjonNavnRiver(
    brregClient: BrregClient,
    isDevelopmentMode: Boolean,
): List<HentOrganisasjonNavnRiver> =
    listOf(
        HentOrganisasjonNavnRiver(brregClient, isDevelopmentMode),
    )
