package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.EventListener

class JournalfoerInntektsmeldingMottattListener(rapidsConnection: RapidsConnection) : EventListener(rapidsConnection) {

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

    override val event: EventName = EventName.INNTEKTSMELDING_MOTTATT

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.requireKey(Key.INNTEKTSMELDING_DOKUMENT.str)
            it.interestedIn(Key.UUID.str)
        }
    }

    override fun onEvent(packet: JsonMessage) {
        val uuid = packet[Key.UUID.str]
        logger.info("Mottatt event ${EventName.INNTEKTSMELDING_MOTTATT} med uuid=$uuid")
        val jsonMessage = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_MOTTATT,
                Key.BEHOV.str to BehovType.JOURNALFOER,
                Key.UUID.str to uuid,
                Key.INNTEKTSMELDING_DOKUMENT.str to packet[Key.INNTEKTSMELDING_DOKUMENT.str]
            )
        )
        publishBehov(jsonMessage)
    }
}
