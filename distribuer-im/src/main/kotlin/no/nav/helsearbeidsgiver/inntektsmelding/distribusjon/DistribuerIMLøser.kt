package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.inntektsmelding.db.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.toJsonElement

class DistribuerIMLøser(private val rapidsConnection: RapidsConnection) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue(Key.EVENT_NAME.str, EventName.INNTEKTSMELDING_MOTTATT.name)
                it.requireKey(Key.INNTEKTSMELDING_DOKUMENT.str)
            }
        }
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Fikk pakke")
        try {
            val inntektsmeldingDokument: InntektsmeldingDokument = packet[Key.INNTEKTSMELDING_DOKUMENT.str].toJsonElement().fromJson()
            sikkerlogg.info("Fikk pakke: $inntektsmeldingDokument")
            val packet: JsonMessage = JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_MOTTATT,
                    Key.INNTEKTSMELDING_DOKUMENT.str to inntektsmeldingDokument
                )
            )
            rapidsConnection.publish(packet.toJson())
        } catch (e: Exception) {
            logger.error("Klarte ikke lese ut inntektsmeldingdokument")
            sikkerlogg.error("Klarte ikke lese ut inntektsmeldingdokument", e)
        }
    }
}
