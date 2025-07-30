package no.nav.helsearbeidsgiver.inntektsmelding.aareg

import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.felles.auth.AuthClient
import no.nav.helsearbeidsgiver.felles.auth.IdentityProvider
import no.nav.helsearbeidsgiver.felles.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.utils.cache.LocalCache
import kotlin.time.Duration.Companion.minutes

fun main() {
    ObjectRiver.connectToRapid {
        createAaregRiver(buildClient())
    }
}

fun createAaregRiver(aaregClient: AaregClient): List<HentAnsettelsesperioderRiver> =
    listOf(
        HentAnsettelsesperioderRiver(aaregClient),
    )

private fun buildClient(): AaregClient {
    val tokenGetter = AuthClient().tokenGetter(IdentityProvider.AZURE_AD, Env.aaregScope)
    return AaregClient(
        baseUrl = Env.aaregUrl,
        cacheConfig = LocalCache.Config(5.minutes, 1000),
        getAccessToken = tokenGetter,
    )
}
