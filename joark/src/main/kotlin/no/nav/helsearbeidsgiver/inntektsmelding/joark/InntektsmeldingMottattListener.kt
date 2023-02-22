package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helse.rapids_rivers.*
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key

class InntektsmeldingMottattListener(private val rapidsConnection: RapidsConnection) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue(Key.EVENT_NAME.str, EventName.INNTEKTSMELDING_MOTTATT.name)
                it.requireKey(Key.INNTEKTSMELDING.str)
                it.interestedIn(Key.UUID.str)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        rapidsConnection.publish(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_MOTTATT.name,
                    Key.BEHOV.str to BehovType.JOURNALFOER.name,
                    Key.UUID.str to packet[Key.UUID.str],
                    Key.INNTEKTSMELDING.str to packet[Key.INNTEKTSMELDING.str]
                )
            ).toJson()
        )
    }
}
