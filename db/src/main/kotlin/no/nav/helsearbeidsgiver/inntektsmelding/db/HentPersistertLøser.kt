@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.HentPersistertLøsning
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import org.slf4j.LoggerFactory

private const val EMPTY_PAYLOAD = "{}"

class HentPersistertLøser(rapidsConnection: RapidsConnection, val repository: InntektsmeldingRepository) : Løser(rapidsConnection) {

    private val BEHOV = BehovType.HENT_PERSISTERT_IM
    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandAll(Key.BEHOV.str, BEHOV)
            it.interestedIn(Key.EVENT_NAME.str)
            it.interestedIn(Key.FORESPOERSEL_ID.str)
        }
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        eventName = EventName.valueOf(packet.get(Key.EVENT_NAME.str).asText())
        onBehov(packet)
    }

    override fun onBehov(packet: JsonMessage) {
        val forespoerselId = packet[Key.FORESPOERSEL_ID.str].asText()
        val transactionId = packet[Key.UUID.str].asText()
        val event = packet[Key.EVENT_NAME.str].asText()
        logger.info("Løser behov $BEHOV med id $forespoerselId")
        sikkerlogg.info("Fikk pakke: ${packet.toJson()}")
        var løsning = HentPersistertLøsning(error = Feilmelding("Klarte ikke hente persistert inntektsmelding"))
        try {
            val dokument = repository.hentNyeste(forespoerselId)
            if (dokument == null) {
                løsning = HentPersistertLøsning("")
            } else {
                sikkerlogg.info("Fant dokument: $dokument")
                løsning = HentPersistertLøsning(dokument.toString())
            }
            publiserLøsning(løsning, packet)
            publiserData(packet, dokument)
        } catch (ex: Exception) {
            logger.info("Klarte ikke hente persistert inntektsmelding")
            sikkerlogg.error("Klarte ikke hente persistert inntektsmelding", ex)
            publiserFeil(transactionId, event, løsning.error)
        }
    }

    private fun publiserFeil(transactionId: String, event: String, error: Feilmelding?) {
        val message = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to event,
                Key.FAIL.str to customObjectMapper().writeValueAsString(error),
                Key.UUID.str to transactionId
            )
        )
        sikkerlogg.info("sender feil: " + message.toJson())
        rapidsConnection.publish(message.toJson())
    }

    fun publiserLøsning(løsning: HentPersistertLøsning, packet: JsonMessage) {
        sikkerlogg.info("sender løsning: " + packet.toJson())
        packet.setLøsning(BEHOV, løsning)
        rapidsConnection.publish(packet.toJson())
    }

    private fun publiserData(packet: JsonMessage, inntektsmeldingDokument: InntektsmeldingDokument?) {
        val transaksjonsId = packet[Key.UUID.str].asText()
        val event = packet[Key.EVENT_NAME.str].asText()
        val packet: JsonMessage = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to event,
                Key.DATA.str to "",
                Key.INNTEKTSMELDING_DOKUMENT.str to if (inntektsmeldingDokument == null) {
                    EMPTY_PAYLOAD
                } else {
                    customObjectMapper().writeValueAsString(
                        inntektsmeldingDokument
                    )
                },
                Key.UUID.str to transaksjonsId
            )
        )
        sikkerlogg.info("Publiserer data" + packet.toJson())
        rapidsConnection.publish(packet.toJson())
    }

    private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
        this[Key.LØSNING.str] = mapOf(
            nøkkel.name to data
        )
    }
}
