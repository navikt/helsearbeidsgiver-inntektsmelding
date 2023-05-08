package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerLogger: Logger = LoggerFactory.getLogger("tjenestekall")
internal val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-joark")

fun main() {
    RapidApplication
        .create(System.getenv())
        .createJoark(buildDokArkivClient(setUpEnvironment()))
        .start()
}

fun RapidsConnection.createJoark(buildDokArkivClient: DokArkivClient): RapidsConnection {
    sikkerLogger.info("Starting JournalførInntektsmeldingLøser...")
    JournalførInntektsmeldingLøser(
        this,
        buildDokArkivClient
    )
    sikkerLogger.info("Starting JournalfoerInntektsmeldingMottattListener...")
    JournalfoerInntektsmeldingMottattListener(this)
    return this
}
