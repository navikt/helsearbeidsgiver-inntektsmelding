package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toPretty
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class LagreJournalpostIdLøser(
    rapidsConnection: RapidsConnection,
    private val repository: InntektsmeldingRepository,
) : Løser(rapidsConnection) {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandValue(Key.BEHOV.str, BehovType.LAGRE_JOURNALPOST_ID.name)
            it.requireKey(Key.UUID.str)
            it.requireKey(Key.JOURNALPOST_ID.str)
        }
    }

    override fun onBehov(packet: JsonMessage) {
        val transaksjonsId = packet[Key.UUID.str].asText()
        val forespoerselId = packet[Key.FORESPOERSEL_ID.str].asText()
        logger.info("LagreJournalpostIdLøser behov ${BehovType.LAGRE_JOURNALPOST_ID.name} med transaksjonsId $transaksjonsId")
        sikkerLogger.info("LagreJournalpostIdLøser fikk pakke:\n${packet.toPretty()}")
        val journalpostId = packet[Key.JOURNALPOST_ID.str].asText()
        if (journalpostId.isNullOrBlank()) {
            logger.error("LagreJournalpostIdLøser fant ingen journalpostId for transaksjonsId $transaksjonsId")
            sikkerLogger.error("LagreJournalpostIdLøser fant ingen journalpostId for transaksjonsId $transaksjonsId")
            publiserFeil(Feilmelding("Klarte ikke lagre journalpostId for transaksjonsId $transaksjonsId. Tom journalpostID!!"), packet)
        } else {
            try {
                repository.oppdaterJournapostId(journalpostId, forespoerselId)
                logger.info("LagreJournalpostIdLøser lagret journalpostId $journalpostId i database for forespoerselId $forespoerselId")
                val inntektsmeldingDokument = repository.hentNyeste(forespoerselId)
                publiser(transaksjonsId, journalpostId, inntektsmeldingDokument!!)
            } catch (ex: Exception) {
                publiserFeil(Feilmelding("Klarte ikke lagre journalpostId for transaksjonsId $transaksjonsId"), packet)
                logger.error("LagreJournalpostIdLøser klarte ikke lagre journalpostId $journalpostId for transaksjonsId $transaksjonsId")
                sikkerLogger.error("LagreJournalpostIdLøser klarte ikke lagre journalpostId $journalpostId for transaksjonsId $transaksjonsId", ex)
            }
        }
    }

    private fun publiser(
        uuid: String,
        journalpostId: String,
        inntektsmeldingDokument: InntektsmeldingDokument
    ) {
        val jsonMessage = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_JOURNALFOERT.name,
                Key.JOURNALPOST_ID.str to journalpostId,
                DataFelt.INNTEKTSMELDING_DOKUMENT.str to inntektsmeldingDokument,
                Key.TRANSACTION_ORIGIN.str to uuid
            )
        )
        publishEvent(jsonMessage)
    }

    private fun publiserFeil(feilmelding: Feilmelding, packet: JsonMessage) {
        val fail = JsonMessage.newMessage(
            mapOf(
                Key.FAIL.str to feilmelding,
                Key.UUID.str to packet[Key.UUID.str]
            )
        )
        publishBehov(fail)
    }
}
