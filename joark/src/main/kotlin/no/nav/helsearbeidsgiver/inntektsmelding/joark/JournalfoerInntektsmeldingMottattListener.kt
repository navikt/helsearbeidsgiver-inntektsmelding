package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.EventListener
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class JournalfoerInntektsmeldingMottattListener(rapidsConnection: RapidsConnection) : EventListener(rapidsConnection) {

    override val event: EventName = EventName.INNTEKTSMELDING_MOTTATT

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.requireKey(DataFelt.INNTEKTSMELDING_DOKUMENT.str)
            it.interestedIn(Key.UUID.str)
            it.interestedIn(Key.FORESPOERSEL_ID.str)
            it.interestedIn(Key.ID.str)
        }
    }

    override fun onEvent(packet: JsonMessage) {
        val txOrigin = packet[Key.TRANSACTION_ORIGIN.str]
        val foresporselId = packet[Key.FORESPOERSEL_ID.str]
        val uuid = UUID.randomUUID().toString()
        logger.info("Mottatt event ${EventName.INNTEKTSMELDING_MOTTATT} med txOrigin=$txOrigin")
        sikkerLogger.info("Mottatt event ${EventName.INNTEKTSMELDING_MOTTATT} med txOrigin=$txOrigin")
        logger.info("Starter ny journalføring transaksjon med uuid:$uuid")
        sikkerLogger.info("Starter ny journalføring transaksjon med uuid:$uuid")
        val jsonMessage = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_MOTTATT,
                Key.BEHOV.str to BehovType.JOURNALFOER,
                Key.UUID.str to uuid,
                Key.FORESPOERSEL_ID.str to foresporselId,
                DataFelt.INNTEKTSMELDING_DOKUMENT.str to packet[DataFelt.INNTEKTSMELDING_DOKUMENT.str]
            )
        )
        publishBehov(jsonMessage)
    }
}
