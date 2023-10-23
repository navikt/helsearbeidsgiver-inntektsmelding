package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.EventListener
import no.nav.helsearbeidsgiver.utils.log.logger
import java.util.UUID

class ForespoerselLagretListener(rapidsConnection: RapidsConnection) : EventListener(rapidsConnection) {

    override val event: EventName = EventName.FORESPØRSEL_LAGRET

    private val logger = logger()

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.requireKey(DataFelt.ORGNRUNDERENHET.str)
            it.requireKey(Key.IDENTITETSNUMMER.str)
            it.requireKey(Key.FORESPOERSEL_ID.str)
        }
    }

    override fun onEvent(packet: JsonMessage) {
        logger.info("Mottatt event ${EventName.FORESPØRSEL_LAGRET}")
        val uuid = UUID.randomUUID()
        val sakEvent = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.SAK_OPPRETT_REQUESTED,
                Key.UUID.str to uuid,
                Key.FORESPOERSEL_ID.str to packet[Key.FORESPOERSEL_ID.str],
                Key.IDENTITETSNUMMER.str to packet[Key.IDENTITETSNUMMER.str],
                DataFelt.ORGNRUNDERENHET.str to packet[DataFelt.ORGNRUNDERENHET.str]
            )
        )
        val oppgaveEvent = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.OPPGAVE_OPPRETT_REQUESTED,
                Key.UUID.str to uuid,
                Key.FORESPOERSEL_ID.str to packet[Key.FORESPOERSEL_ID.str],
                DataFelt.ORGNRUNDERENHET.str to packet[DataFelt.ORGNRUNDERENHET.str]
            )
        )
        rapidsConnection.publish(sakEvent.toJson())
        rapidsConnection.publish(oppgaveEvent.toJson())
    }
}
