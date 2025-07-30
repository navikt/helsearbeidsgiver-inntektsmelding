package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import no.nav.helsearbeidsgiver.felles.auth.AuthClient
import no.nav.helsearbeidsgiver.felles.auth.IdentityProvider
import no.nav.helsearbeidsgiver.felles.rr.river.ObjectRiver

fun main() {
    val tokenGetter = AuthClient().tokenGetter(IdentityProvider.AZURE_AD, Env.spinnScope)
    val spinnKlient = SpinnKlient(Env.spinnUrl, tokenGetter)

    ObjectRiver.connectToRapid {
        createHentEksternImRiver(spinnKlient)
    }
}

fun createHentEksternImRiver(spinnKlient: SpinnKlient): List<HentEksternImRiver> =
    listOf(
        HentEksternImRiver(spinnKlient),
    )
