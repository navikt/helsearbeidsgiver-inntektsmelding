@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.JournalførtLøsning
import no.nav.helsearbeidsgiver.felles.JournalpostLøsning
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import org.slf4j.LoggerFactory

class LagreJournalpostIdLøser(rapidsConnection: RapidsConnection, val repository: Repository) : River.PacketListener {

    private val BEHOV = BehovType.JOURNALFOER
    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll(Key.BEHOV.str, BEHOV)
                it.requireKey(Key.UUID.str)
                it.requireKey(Key.LØSNING.str)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val uuid = packet[Key.UUID.str].asText()
        logger.info("Løser behov $BEHOV med id $uuid")
        sikkerlogg.info("Fikk pakke: ${packet.toJson()}")
        val journalpostLøsning: JournalpostLøsning = packet[Key.LØSNING.str].toJsonElement().fromJson()
        var løsning = JournalførtLøsning(error = Feilmelding("Klarte ikke lagre journalpostId for $uuid fordi den var tom"))
        journalpostLøsning.value?.let {
            try {
                repository.oppdaterJournapostId(it, uuid)
                løsning = JournalførtLøsning(it)
                logger.info("Lagret journalpostId $it i database for $uuid")
            } catch (ex: Exception) {
                løsning = JournalførtLøsning(error = Feilmelding("Klarte ikke lagre journalpostId $it for $uuid"))
                logger.info("Klarte ikke lagre journalpostId $it for $uuid")
                sikkerlogg.error("Klarte ikke lagre journalpostId $it for $uuid", ex)
            }
        }
        publiserLøsning(løsning, packet, context)
    }

    fun publiserLøsning(løsning: JournalførtLøsning, packet: JsonMessage, context: MessageContext) {
        packet.setLøsning(BehovType.JOURNALFØRT_OK, løsning)
        context.publish(packet.toJson())
    }

    private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
        this[Key.LØSNING.str] = mapOf(
            nøkkel.name to data
        )
    }
}
