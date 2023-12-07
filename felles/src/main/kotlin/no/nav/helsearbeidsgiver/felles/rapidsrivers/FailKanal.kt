package no.nav.helsearbeidsgiver.felles.rapidsrivers

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.utils.json.fromJson

// vi kan vurdere å bruke event feltet og dispatche event istedenfor Fail.
abstract class FailKanal(val rapidsConnection: RapidsConnection) : River.PacketListener {
    abstract val eventName: EventName

    init {
        River(rapidsConnection).apply {
            validate { msg ->
                msg.demand(
                    Key.FAIL to { it.fromJson(Fail.serializer()) }
                )
                msg.demandValues(
                    Key.EVENT_NAME to eventName.name
                )
                msg.rejectKey(Key.BEHOV.str)
                msg.rejectKey(Key.LØSNING.str)
                msg.rejectKey(Key.DATA.str)
                msg.interestedIn(Key.UUID.str)
                msg.interestedIn(Key.FORESPOERSEL_ID.str)
            }
        }
            .register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        onFail(packet)
    }

    abstract fun onFail(packet: JsonMessage)
}
