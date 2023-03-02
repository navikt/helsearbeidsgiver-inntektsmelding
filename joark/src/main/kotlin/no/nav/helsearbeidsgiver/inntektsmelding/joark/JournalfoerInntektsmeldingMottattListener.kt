package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key

class JournalfoerInntektsmeldingMottattListener(private val rapidsConnection: RapidsConnection) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue(Key.EVENT_NAME.str, EventName.INNTEKTSMELDING_MOTTATT.name)
                it.rejectKey(Key.BEHOV.str)
                it.requireKey(Key.INNTEKTSMELDING_DOKUMENT.str)
                it.interestedIn(Key.UUID.str)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val uuid = packet[Key.UUID.str]
        logger.info("Mottatt event ${EventName.INNTEKTSMELDING_MOTTATT} med uuid=$uuid")
        val jsonMessage = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_MOTTATT,
                Key.BEHOV.str to BehovType.JOURNALFOER,
                Key.UUID.str to uuid,
                Key.INNTEKTSMELDING_DOKUMENT.str to packet[Key.INNTEKTSMELDING_DOKUMENT.str]
            )
        ).toJson()
        rapidsConnection.publish(jsonMessage)
    }
}
