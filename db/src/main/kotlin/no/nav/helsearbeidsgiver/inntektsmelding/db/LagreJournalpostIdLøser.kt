@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.LagreJournalpostLøsning
import org.slf4j.LoggerFactory

class LagreJournalpostIdLøser(rapidsConnection: RapidsConnection, val repository: Repository) : River.PacketListener {

    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue(Key.BEHOV.str, BehovType.LAGRE_JOURNALPOST_ID.name)
                it.requireKey(Key.UUID.str)
                it.requireKey(Key.JOURNALPOST_ID.str)
                it.rejectKey(Key.LØSNING.str)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
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
            } catch (ex: Exception) {
                løsning = LagreJournalpostLøsning(error = Feilmelding("Klarte ikke lagre journalpostId for $uuid"))
                logger.info("Klarte ikke lagre journalpostId $journalpostId for $uuid")
                sikkerlogg.error("Klarte ikke lagre journalpostId $journalpostId for $uuid", ex)
            }
        }
        publiserLøsning(løsning, packet, context)
    }

    fun publiserLøsning(løsning: LagreJournalpostLøsning, packet: JsonMessage, context: MessageContext) {
        packet.setLøsning(BehovType.LAGRE_JOURNALPOST_ID, løsning)
        context.publish(packet.toJson())
    }

    private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
        this[Key.LØSNING.str] = mapOf(
            nøkkel.name to data
        )
    }
}
