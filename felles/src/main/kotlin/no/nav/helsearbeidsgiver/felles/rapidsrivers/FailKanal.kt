package no.nav.helsearbeidsgiver.felles.rapidsrivers

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.utils.json.fromJson

class FailKanal(
    val rapid: RapidsConnection,
    private val event: EventName,
    private val onFail: (JsonMessage, MessageContext) -> Unit
) : River.PacketListener {
    init {
        River(rapid).apply {
            validate { msg ->
                msg.demand(
                    Key.FAIL to { it.fromJson(Fail.serializer()) }
                )
                msg.demandValues(
                    Key.EVENT_NAME to event.name
                )
                msg.requireKeys(Key.UUID)
                msg.interestedIn(Key.FORESPOERSEL_ID)
                msg.rejectKeys(
                    Key.BEHOV,
                    Key.LØSNING,
                    Key.DATA
                )
            }
        }
            .register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        onFail(packet, context)
    }
}
