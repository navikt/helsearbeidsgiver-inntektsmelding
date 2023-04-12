@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import org.slf4j.LoggerFactory

class LagreJournalpostIdLøser(rapidsConnection: RapidsConnection, val repository: Repository) : Løser(rapidsConnection) {

    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandValue(Key.BEHOV.str, BehovType.LAGRE_JOURNALPOST_ID.name)
            it.requireKey(Key.UUID.str)
            it.requireKey(Key.JOURNALPOST_ID.str)
        }
    }

    override fun onBehov(packet: JsonMessage) {
        val uuid = packet[Key.UUID.str].asText()
        logger.info("LagreJournalpostIdLøser behov ${BehovType.LAGRE_JOURNALPOST_ID.name} med id $uuid")
        sikkerlogg.info("LagreJournalpostIdLøser fikk pakke: ${packet.toJson()}")
        val journalpostId = packet[Key.JOURNALPOST_ID.str].asText()
        if (journalpostId.isNullOrBlank()) {
            logger.error("LagreJournalpostIdLøser fant ingen journalpostId for $uuid")
            sikkerlogg.error("LagreJournalpostIdLøser fant ingen journalpostId for $uuid")
            publiserFeil(Feilmelding("Klarte ikke lagre journalpostId for $uuid. Tom journalpostID!!"), packet)
        } else {
            try {
                repository.oppdaterJournapostId(journalpostId, uuid)
                logger.info("LagreJournalpostIdLøser lagret journalpostId $journalpostId i database for $uuid")
                val inntektsmeldingDokument = repository.hentNyeste(uuid)
                publiser(uuid, journalpostId, inntektsmeldingDokument!!)
            } catch (ex: Exception) {
                publiserFeil(Feilmelding("Klarte ikke lagre journalpostId for $uuid"), packet)
                logger.error("LagreJournalpostIdLøser klarte ikke lagre journalpostId $journalpostId for $uuid")
                sikkerlogg.error("LagreJournalpostIdLøser klarte ikke lagre journalpostId $journalpostId for $uuid", ex)
            }
        }
    }

    fun publiser(
        uuid: String,
        journalpostId: String,
        inntektsmeldingDokument: InntektsmeldingDokument
    ) {
        val oppgaveId = repository.hentOppgaveId(uuid)
        logger.info("Fant oppgaveId $oppgaveId for forespørselId $uuid")
        val sakId = repository.hentSakId(uuid)
        logger.info("Fant sakId $sakId for forespørselId $uuid")
        val jsonMessage = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_JOURNALFOERT,
                Key.JOURNALPOST_ID.str to journalpostId,
                Key.OPPGAVE_ID.str to oppgaveId!!, // TODO Lag bedre feilhåndtering dersom oppgaveId ikke ble funnet i db
                Key.SAK_ID.str to sakId!!, // TODO Lag bedre feilhåndtering dersom oppgaveId ikke ble funnet i db
                Key.UUID.str to uuid,
                Key.INNTEKTSMELDING_DOKUMENT.str to inntektsmeldingDokument
            )
        )
        publishEvent(jsonMessage)
    }

    fun publishLagret(uuid: String) {
        val message = JsonMessage.newMessage(
            mapOf(
                Key.DATA.str to "Lagret",
                Key.UUID.str to uuid
            )
        )
        this.publishData(message)
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
