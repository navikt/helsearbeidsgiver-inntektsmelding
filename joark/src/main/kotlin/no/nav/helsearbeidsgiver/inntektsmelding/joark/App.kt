package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "helsearbeidsgiver-im-joark".logger()

fun main() {
    RapidApplication
        .create(System.getenv())
        .createJoark(buildDokArkivClient(setUpEnvironment()))
        .start()
}

fun RapidsConnection.createJoark(buildDokArkivClient: DokArkivClient): RapidsConnection {
    logger.info("Starting JournalførInntektsmeldingLøser...")
    JournalførInntektsmeldingLøser(
        this,
        buildDokArkivClient
    )
    logger.info("Starting JournalfoerInntektsmeldingMottattListener...")
    JournalfoerInntektsmeldingMottattListener(this)
    return this
}
