@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.LagreJournalpostLøsning
import no.nav.helsearbeidsgiver.felles.inntektsmelding.db.InntektsmeldingDokument
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
        logger.info("Løser behov ${BehovType.LAGRE_JOURNALPOST_ID.name} med id $uuid")
        sikkerlogg.info("Fikk pakke: ${packet.toJson()}")
        val journalpostId = packet[Key.JOURNALPOST_ID.str].asText()
        var løsning = LagreJournalpostLøsning(journalpostId)
        if (journalpostId.isNullOrBlank()) {
            løsning = LagreJournalpostLøsning(error = Feilmelding("Klarte ikke lagre journalpostId for $uuid. Tom journalpostID!!"))
            logger.error("Ingen journalpostId for $uuid")
            sikkerlogg.error("Ingen journalpostId for $uuid")
        } else {
            try {
                repository.oppdaterJournapostId(journalpostId, uuid)
                logger.info("Lagret journalpostId $journalpostId i database for $uuid")
                val inntektsmeldingDokument = repository.hentNyeste(uuid)
                publiser(journalpostId, inntektsmeldingDokument!!)
            } catch (ex: Exception) {
                løsning = LagreJournalpostLøsning(error = Feilmelding("Klarte ikke lagre journalpostId for $uuid"))
                logger.info("Klarte ikke lagre journalpostId $journalpostId for $uuid")
                sikkerlogg.error("Klarte ikke lagre journalpostId $journalpostId for $uuid", ex)
            }
        }
        publiserLøsning(løsning, packet)
    }

    fun publiser(journalpostId: String, inntektsmeldingDokument: InntektsmeldingDokument) {
        val jsonMessage = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_JOURNALFOERT,
                Key.JOURNALPOST_ID.str to journalpostId,
                Key.INNTEKTSMELDING_DOKUMENT.str to inntektsmeldingDokument
            )
        )
        publishEvent(jsonMessage)
    }

    fun publiserLøsning(løsning: LagreJournalpostLøsning, packet: JsonMessage) {
        packet.setLøsning(BehovType.LAGRE_JOURNALPOST_ID, løsning)
        publishBehov(packet)
    }

    private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
        this[Key.LØSNING.str] = mapOf(
            nøkkel.name to data
        )
    }
}
