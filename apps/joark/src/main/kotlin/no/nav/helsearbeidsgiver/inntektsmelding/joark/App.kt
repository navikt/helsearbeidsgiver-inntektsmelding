package no.nav.helsearbeidsgiver.inntektsmelding.joark

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.felles.auth.AuthClient
import no.nav.helsearbeidsgiver.felles.auth.IdentityProvider
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-joark".logger()

fun main() {
    RapidApplication
        .create(System.getenv())
        .createJournalfoerImRiver(createDokArkivClient())
        .start()
}

fun RapidsConnection.createJournalfoerImRiver(dokArkivClient: DokArkivClient): RapidsConnection =
    also {
        logger.info("Starter ${JournalfoerImRiver::class.simpleName}...")
        JournalfoerImRiver(dokArkivClient).connect(this)
    }

private fun createDokArkivClient(): DokArkivClient {
    val tokenGetter = AuthClient().tokenGetter(IdentityProvider.AZURE_AD, Env.dokArkivScope)
    return DokArkivClient(
        url = Env.dokArkivUrl,
        maxRetries = 3,
        getAccessToken = tokenGetter,
    )
}
