package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import no.nav.helsearbeidsgiver.felles.auth.AuthClient
import no.nav.helsearbeidsgiver.felles.auth.IdentityProvider
import no.nav.helsearbeidsgiver.felles.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.utils.cache.LocalCache
import kotlin.time.Duration.Companion.minutes

fun main() {
    ObjectRiver.connectToRapid {
        createHentInntektRiver(createInntektKlient())
    }
}

fun createHentInntektRiver(inntektKlient: InntektKlient): List<HentInntektRiver> =
    listOf(
        HentInntektRiver(inntektKlient),
    )

fun createInntektKlient(): InntektKlient {
    val tokenGetter = AuthClient().tokenGetter(IdentityProvider.AZURE_AD, Env.inntektScope)
    return InntektKlient(
        baseUrl = Env.inntektUrl,
        cacheConfig = LocalCache.Config(10.minutes, 1000),
        getAccessToken = tokenGetter,
    )
}
