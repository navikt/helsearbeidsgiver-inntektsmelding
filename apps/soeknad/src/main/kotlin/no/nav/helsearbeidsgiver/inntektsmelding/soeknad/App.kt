package no.nav.helsearbeidsgiver.inntektsmelding.soeknad

import no.nav.hag.simba.utils.auth.AuthClient
import no.nav.hag.simba.utils.auth.IdentityProvider
import no.nav.helsearbeidsgiver.inntektsmelding.soeknad.klient.SoeknadKlient
import no.nav.helsearbeidsgiver.utils.cache.LocalCache
import kotlin.time.Duration.Companion.hours

fun main() {
    createSoeknadKlient()
}

private fun createSoeknadKlient(): SoeknadKlient =
    SoeknadKlient(
        baseUrl = Env.soeknadBaseUrl,
        cacheConfig = LocalCache.Config(1.hours, 100),
        getAccessToken = AuthClient().tokenGetter(IdentityProvider.AZURE_AD, Env.soeknadScope),
    )
