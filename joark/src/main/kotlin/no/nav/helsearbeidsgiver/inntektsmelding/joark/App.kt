package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.tokenprovider.oauth2ClientCredentialsTokenGetter
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

        logger.info("Starter ${JournalfoerInntektsmeldingLoeser::class.simpleName}...")
        JournalfoerInntektsmeldingLoeser(this, dokArkivClient)
    }

private fun createDokArkivClient(): DokArkivClient {
    val tokenGetter = oauth2ClientCredentialsTokenGetter(Env.oauth2Environment)
    return DokArkivClient(
        url = Env.dokArkivUrl,
        maxRetries = 3,
        getAccessToken = tokenGetter
    )
}
