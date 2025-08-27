package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.felles.auth.AuthClient
import no.nav.helsearbeidsgiver.felles.auth.IdentityProvider
import no.nav.helsearbeidsgiver.felles.rr.river.ObjectRiver

fun main() {
    ObjectRiver.connectToRapid {
        createJournalfoerImRiver(createDokArkivClient())
    }
}

fun createJournalfoerImRiver(dokArkivClient: DokArkivClient): List<JournalfoerImRiver> =
    listOf(
        JournalfoerImRiver(dokArkivClient),
    )

private fun createDokArkivClient(): DokArkivClient {
    val tokenGetter = AuthClient().tokenGetter(IdentityProvider.AZURE_AD, Env.dokArkivScope)
    return DokArkivClient(Env.dokArkivUrl, tokenGetter)
}
