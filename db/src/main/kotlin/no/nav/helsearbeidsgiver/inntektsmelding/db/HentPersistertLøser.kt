@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import kotlin.system.measureTimeMillis

private const val EMPTY_PAYLOAD = "{}"

class HentPersistertLøser(rapidsConnection: RapidsConnection, private val repository: InntektsmeldingRepository) : Løser(rapidsConnection) {

    private val BEHOV = BehovType.HENT_PERSISTERT_IM
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandAll(Key.BEHOV.str, BEHOV)
            it.interestedIn(Key.EVENT_NAME.str)
            it.interestedIn(Key.FORESPOERSEL_ID.str)
        }
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        onBehov(packet)
    }

    override fun onBehov(packet: JsonMessage) {
        measureTimeMillis {
            val forespoerselId = packet[Key.FORESPOERSEL_ID.str].asText()
            val transactionId = packet[Key.UUID.str].asText()
            val event = packet[Key.EVENT_NAME.str].asText()
            logger.info("Skal hente persistert inntektsmelding med forespørselId $forespoerselId")
            sikkerLogger.info("Skal hente persistert inntektsmelding med forespørselId $forespoerselId")
            sikkerLogger.info("Skal hente persistert inntektsmelding for pakke: ${packet.toJson()}")
            try {
                val dokument = repository.hentNyeste(forespoerselId)
                if (dokument == null) {
                    logger.info("Fant IKKE persistert inntektsmelding for forespørselId $forespoerselId")
                    sikkerLogger.info("Fant IKKE persistert inntektsmelding for forespørselId $forespoerselId")
                } else {
                    sikkerLogger.info("Fant persistert inntektsmelding: $dokument for forespørselId $forespoerselId")
                }
                publiserData(packet, dokument)
            } catch (ex: Exception) {
                logger.info("Det oppstod en feil ved uthenting av persistert inntektsmelding for forespørselId $forespoerselId")
                sikkerLogger.error("Det oppstod en feil ved uthenting av persistert inntektsmelding for forespørselId $forespoerselId", ex)
                publiserFeil(transactionId, event, Feilmelding("Klarte ikke hente persistert inntektsmelding"))
            }
        }.also {
            logger.info("Hent inntektmelding fra DB took $it")
        }
    }

    private fun publiserFeil(transactionId: String, event: String, forespoerselId: String, error: Feilmelding?) {
        val message = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to event,
                Key.FAIL.str to customObjectMapper().writeValueAsString(error),
                Key.UUID.str to transactionId,
                Key.FORESPOERSEL_ID.str to forespoerselId
            )
        )
        sikkerLogger.info("sender feil: " + message.toJson())
        rapidsConnection.publish(message.toJson())
    }

    private fun publiserData(packet: JsonMessage, inntektsmeldingDokument: InntektsmeldingDokument?) {
        val transaksjonsId = packet[Key.UUID.str].asText()
        val forespoerselId = packet[Key.FORESPOERSEL_ID.str].asText()
        val event = packet[Key.EVENT_NAME.str].asText()
        val packet: JsonMessage = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to event,
                Key.DATA.str to "",
                DataFelt.INNTEKTSMELDING_DOKUMENT.str to if (inntektsmeldingDokument == null) {
                    EMPTY_PAYLOAD
                } else {
                    customObjectMapper().writeValueAsString(
                        inntektsmeldingDokument
                    )
                },
                Key.UUID.str to transaksjonsId,
                Key.FORESPOERSEL_ID.str to forespoerselId
            )
        )
        sikkerLogger.info("Publiserer data" + packet.toJson())
        rapidsConnection.publish(packet.toJson())
    }
}
