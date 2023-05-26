package no.nav.helsearbeidsgiver.felles.rapidsrivers

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
// vi kan vurdere å bruke event feltet og dispatche event istedenfor Fail.
abstract class FailKanal(val rapidsConnection: RapidsConnection) : River.PacketListener {
    abstract val eventName: EventName

    init {
        configure(
            River(rapidsConnection).apply {
                validate(accept())
            }
        ).register(this)
    }

    protected fun accept(): River.PacketValidation {
        return River.PacketValidation { }
    }

    protected fun configure(river: River): River {
        return river.validate {
            it.demandValue(Key.EVENT_NAME.str, eventName.name)
            it.demandKey(Key.FAIL.str)
            it.rejectKey(Key.BEHOV.str)
            it.rejectKey(Key.LØSNING.str)
            it.rejectKey(Key.DATA.str)
            it.requireKey(Key.UUID.str)
            it.interestedIn(Key.FORESPOERSEL_ID.str)
        }
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        onFail(packet)
    }

    abstract fun onFail(packet: JsonMessage)
}
