package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key

class InntektsmeldingJournalførtListener(private val rapidsConnection: RapidsConnection) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue(Key.EVENT_NAME.str, EventName.INNTEKTSMELDING_JOURNALFØRT.name)
                it.rejectKey(Key.BEHOV.str)
                it.requireKey(Key.INNTEKTSMELDING_DOKUMENT.str)
                it.requireKey(Key.JOURNALPOST_ID.str)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Inntektsmelding Mottat med packet ${packet.toJson()}")
        val jsonMessage = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_JOURNALFØRT.name,
                Key.BEHOV.str to BehovType.DISTRIBUER_IM.name,
                Key.JOURNALPOST_ID.str to packet[Key.JOURNALPOST_ID.str].asText(),
                Key.INNTEKTSMELDING_DOKUMENT.str to packet[Key.INNTEKTSMELDING_DOKUMENT.str]
            )
        ).toJson()
        logger.info("Inntektsmelding Journalført new pakke er $jsonMessage")
        rapidsConnection.publish(jsonMessage)
    }
}
