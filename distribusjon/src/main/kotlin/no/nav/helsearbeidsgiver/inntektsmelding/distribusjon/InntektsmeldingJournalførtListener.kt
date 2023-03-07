package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.EventListener

class InntektsmeldingJournalførtListener(rapidsConnection: RapidsConnection) : EventListener(rapidsConnection) {

    override val event: EventName = EventName.INNTEKTSMELDING_JOURNALFOERT

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.requireKey(Key.INNTEKTSMELDING_DOKUMENT.str)
            it.requireKey(Key.JOURNALPOST_ID.str)
        }
    }

    override fun onEvent(packet: JsonMessage) {
        logger.info("Mottatt event ${EventName.INNTEKTSMELDING_JOURNALFOERT}")
        sikkerlogg.info("Mottatt event ${EventName.INNTEKTSMELDING_JOURNALFOERT} med pakke ${packet.toJson()}")
        val jsonMessage = JsonMessage.newMessage(
            mapOf(
                Key.BEHOV.str to BehovType.DISTRIBUER_IM.name,
                Key.JOURNALPOST_ID.str to packet[Key.JOURNALPOST_ID.str].asText(),
                Key.INNTEKTSMELDING_DOKUMENT.str to packet[Key.INNTEKTSMELDING_DOKUMENT.str]
            )
        )
        publishBehov(jsonMessage)
    }
}
