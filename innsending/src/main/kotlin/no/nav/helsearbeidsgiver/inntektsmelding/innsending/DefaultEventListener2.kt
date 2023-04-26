package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DataFields
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener

open class DefaultEventListener2(
    val dataFelter: DataFields,
    override val redisStore: RedisStore,
    override val event: EventName,
    open val rapidsConnection: RapidsConnection
) : CompositeEventListener(redisStore) {

    fun start() {
        withEventListener {
            StatefullEventListener(redisStore, event, dataFelter.IN.toTypedArray(), this@DefaultEventListener2, this.rapidsConnection)
        }

        withDataKanal {
            StatefullDataKanal(
                dataFelter.OUT.toTypedArray(),
                this.event,
                this@DefaultEventListener2,
                this@DefaultEventListener2.rapidsConnection,
                this@DefaultEventListener2.redisStore
            )
        }
        withFailKanal { DelegatingFailKanal(this.event, this@DefaultEventListener2, this@DefaultEventListener2.rapidsConnection) }
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
