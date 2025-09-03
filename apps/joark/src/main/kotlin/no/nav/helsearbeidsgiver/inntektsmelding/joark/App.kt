package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.hag.simba.utils.auth.AuthClient
import no.nav.hag.simba.utils.auth.IdentityProvider
import no.nav.hag.simba.utils.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient

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
