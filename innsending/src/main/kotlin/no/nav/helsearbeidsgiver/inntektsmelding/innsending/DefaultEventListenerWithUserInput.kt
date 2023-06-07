package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import jdk.jfr.Experimental
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.InputFelter
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.IRedisStore

@Experimental
open class DefaultEventListenerWithUserInput(
    private val dataFelter: InputFelter,
    override val redisStore: IRedisStore,
    override val event: EventName,
    open val rapidsConnection: RapidsConnection
) : CompositeEventListener(redisStore) {

    fun start() {
        withEventListener {
            StatefullEventListener(redisStore, event, dataFelter.IN.toTypedArray(), this@DefaultEventListenerWithUserInput, this.rapidsConnection)
        }

        withDataKanal {
            StatefullDataKanal(
                dataFelter.OUT.toTypedArray(),
                this.event,
                this@DefaultEventListenerWithUserInput,
                this@DefaultEventListenerWithUserInput.rapidsConnection,
                this@DefaultEventListenerWithUserInput.redisStore
            )
        }
        withFailKanal { DelegatingFailKanal(this.event, this@DefaultEventListenerWithUserInput, this@DefaultEventListenerWithUserInput.rapidsConnection) }
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
