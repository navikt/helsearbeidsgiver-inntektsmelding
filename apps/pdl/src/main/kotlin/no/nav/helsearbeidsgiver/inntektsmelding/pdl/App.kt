package no.nav.helsearbeidsgiver.inntektsmelding.pdl

import no.nav.helsearbeidsgiver.felles.auth.AuthClient
import no.nav.helsearbeidsgiver.felles.auth.IdentityProvider
import no.nav.helsearbeidsgiver.felles.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.pdl.Behandlingsgrunnlag
import no.nav.helsearbeidsgiver.pdl.PdlClient
import no.nav.helsearbeidsgiver.utils.cache.LocalCache
import kotlin.time.Duration.Companion.days

fun main() {
    ObjectRiver.connectToRapid {
        createHentPersonerRiver(buildClient())
    }
}

fun createHentPersonerRiver(pdlClient: PdlClient): List<HentPersonerRiver> =
    listOf(
        HentPersonerRiver(pdlClient),
    )

fun buildClient(): PdlClient {
    val tokenGetter = AuthClient().tokenGetter(IdentityProvider.AZURE_AD, Env.pdlScope)
    return PdlClient(
        url = Env.pdlUrl,
        behandlingsgrunnlag = Behandlingsgrunnlag.INNTEKTSMELDING,
        cacheConfig = LocalCache.Config(1.days, 10_000),
        getAccessToken = tokenGetter,
    )
}
