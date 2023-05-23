@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.db

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
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class LagreJournalpostIdLøser(
    rapidsConnection: RapidsConnection,
    val repository: InntektsmeldingRepository,
    val forespoerselRepository: ForespoerselRepository
) :
    Løser(rapidsConnection) {

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
        sikkerLogger.info("LagreJournalpostIdLøser fikk pakke: ${packet.toJson()}")
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
                publiser(transaksjonsId, forespoerselId, journalpostId, inntektsmeldingDokument!!)
            } catch (ex: Exception) {
                publiserFeil(Feilmelding("Klarte ikke lagre journalpostId for transaksjonsId $transaksjonsId"), packet)
                logger.error("LagreJournalpostIdLøser klarte ikke lagre journalpostId $journalpostId for transaksjonsId $transaksjonsId")
                sikkerLogger.error("LagreJournalpostIdLøser klarte ikke lagre journalpostId $journalpostId for transaksjonsId $transaksjonsId", ex)
            }
        }
    }

    fun publiser(
        uuid: String,
        forespoerselId: String,
        journalpostId: String,
        inntektsmeldingDokument: InntektsmeldingDokument
    ) {
        val oppgaveId = forespoerselRepository.hentOppgaveId(forespoerselId)
        logger.info("Fant oppgaveId $oppgaveId for forespørselId $forespoerselId")
        val sakId = forespoerselRepository.hentSakId(forespoerselId)
        logger.info("Fant sakId $sakId for forespørselId $forespoerselId")
        val jsonMessage = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_JOURNALFOERT.name,
                Key.JOURNALPOST_ID.str to journalpostId,
                DataFelt.OPPGAVE_ID.str to oppgaveId!!, // TODO Lag bedre feilhåndtering dersom oppgaveId ikke ble funnet i db
                DataFelt.SAK_ID.str to sakId!!, // TODO Lag bedre feilhåndtering dersom oppgaveId ikke ble funnet i db
                Key.TRANSACTION_ORIGIN.str to uuid,
                Key.INNTEKTSMELDING_DOKUMENT.str to inntektsmeldingDokument
            )
        )
        publishEvent(jsonMessage)
    }

    fun publiserFeil(feilmelding: Feilmelding, packet: JsonMessage) {
        val fail = JsonMessage.newMessage(
            mapOf(
                Key.FAIL.str to feilmelding,
                Key.UUID.str to packet[Key.UUID.str]
            )
        )
        publishBehov(fail)
    }

    private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
        this[Key.LØSNING.str] = mapOf(
            nøkkel.name to data
        )
    }
}
