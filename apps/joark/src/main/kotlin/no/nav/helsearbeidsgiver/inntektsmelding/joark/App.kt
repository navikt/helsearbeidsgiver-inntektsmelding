package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.felles.oauth2.OAuth2ClientConfig
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-joark".logger()

fun main() {
    RapidApplication
        .create(System.getenv())
        .createJoark(createDokArkivClient(setUpEnvironment()))
        .start()
}

fun RapidsConnection.createJoark(dokArkivClient: DokArkivClient): RapidsConnection =
    also {
        logger.info("Starter ${JournalfoerInntektsmeldingLoeser::class.simpleName}...")
        JournalfoerInntektsmeldingLoeser(this, dokArkivClient)

        logger.info("Starter ${JournalfoerInntektsmeldingMottattListener::class.simpleName}...")
        JournalfoerInntektsmeldingMottattListener(this)
    }

private fun createDokArkivClient(environment: Environment): DokArkivClient {
    val tokenProvider = OAuth2ClientConfig(environment.azureOAuthEnvironment)
    return DokArkivClient(
        url = environment.dokarkivUrl,
        maxRetries = 3,
        getAccessToken = tokenProvider::getToken
    )
}
