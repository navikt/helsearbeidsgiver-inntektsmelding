@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.HentPersistertLøsning
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import org.slf4j.LoggerFactory

class HentPersistertLøser(rapidsConnection: RapidsConnection, val repository: InntektsmeldingRepository) : River.PacketListener {

    private val BEHOV = BehovType.HENT_PERSISTERT_IM
    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        logger.info("Starter HentPersistertLøser...")
        River(rapidsConnection).apply {
            validate {
                it.demandAll(Key.BEHOV.str, BEHOV)
                it.requireKey(Key.UUID.str)
                it.requireKey(Key.INITIATE_ID.str)
                it.interestedIn(Key.EVENT_NAME.str)
                it.rejectKey(Key.LØSNING.str)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val uuid = packet[Key.UUID.str].asText()
        val event = packet[Key.EVENT_NAME.str].asText()
        val transactionId = packet[Key.INITIATE_ID.str].asText()
        logger.info("Løser behov $BEHOV med id $uuid")
        sikkerlogg.info("Fikk pakke: ${packet.toJson()}")
        var løsning = HentPersistertLøsning(error = Feilmelding("Klarte ikke hente persistert inntektsmelding"))
        try {
            val dokument = repository.hentNyeste(uuid)
            if (dokument == null) {
                løsning = HentPersistertLøsning("")
            } else {
                sikkerlogg.info("Fant dokument: $dokument")
                løsning = HentPersistertLøsning(dokument.toString())
            }
        } catch (ex: Exception) {
            logger.info("Klarte ikke hente persistert inntektsmelding")
            sikkerlogg.error("Klarte ikke hente persistert inntektsmelding", ex)
            publiserFeil(uuid, transactionId, event, løsning.error, context)
        }
        publiserLøsning(løsning, packet, context)
    }

    private fun publiserFeil(uuid: String, transactionId: String, event: String, error: Feilmelding?, context: MessageContext) {
        val message = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to event,
                Key.FAIL.str to customObjectMapper().writeValueAsString(error),
                Key.UUID.str to uuid,
                Key.INITIATE_ID.str to transactionId
            )
        )
        context.publish(message.toJson())
    }

    fun publiserLøsning(løsning: HentPersistertLøsning, packet: JsonMessage, context: MessageContext) {
        packet.setLøsning(BEHOV, løsning)
        context.publish(packet.toJson())
    }

    private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
        this[Key.LØSNING.str] = mapOf(
            nøkkel.name to data
        )
    }
}
