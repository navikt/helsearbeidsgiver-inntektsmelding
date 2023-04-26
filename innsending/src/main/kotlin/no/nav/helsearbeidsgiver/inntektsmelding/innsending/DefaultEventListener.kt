package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal

open class DefaultEventListener(
    val dataFelter: Array<String>,
    override val redisStore: RedisStore,
    override val event: EventName,
    open val rapidsConnection: RapidsConnection
) : CompositeEventListener(redisStore) {

    fun start() {
        withEventListener {
            object : DelegatingEventListener(this@DefaultEventListener, this.rapidsConnection) {
                override val event: EventName = this@DefaultEventListener.event

                override fun accept(): River.PacketValidation = River.PacketValidation { }
            }
        }

        withDataKanal {
            StatefullDataKanal(
                dataFelter,
                this.event,
                this@DefaultEventListener,
                this@DefaultEventListener.rapidsConnection,
                this@DefaultEventListener.redisStore
            )
        }
        withFailKanal { DelegatingFailKanal(this.event, this@DefaultEventListener, this@DefaultEventListener.rapidsConnection) }
    }
    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        TODO("Not yet implemented")
    }

    override fun finalize(message: JsonMessage) {
        TODO("Not yet implemented")
    }

    override fun terminate(message: JsonMessage) {
        TODO("Not yet implemented")
    }
}
