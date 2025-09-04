package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import no.nav.hag.simba.utils.auth.AuthClient
import no.nav.hag.simba.utils.auth.IdentityProvider
import no.nav.hag.simba.utils.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.altinn.Altinn3M2MClient
import no.nav.helsearbeidsgiver.utils.cache.LocalCache
import kotlin.time.Duration.Companion.minutes

fun main() {
    ObjectRiver.connectToRapid {
        createAltinn(createAltinnClient())
    }
}

fun createAltinn(altinnClient: Altinn3M2MClient): List<ObjectRiver.Simba<*>> =
    listOf(
        TilgangRiver(altinnClient),
        AltinnRiver(altinnClient),
    )

private fun createAltinnClient(): Altinn3M2MClient =
    Altinn3M2MClient(
        baseUrl = Env.altinnTilgangerBaseUrl,
        serviceCode = Env.serviceCode,
        cacheConfig = LocalCache.Config(60.minutes, 5000),
        getToken = AuthClient().tokenGetter(IdentityProvider.AZURE_AD, Env.altinnScope),
    )
