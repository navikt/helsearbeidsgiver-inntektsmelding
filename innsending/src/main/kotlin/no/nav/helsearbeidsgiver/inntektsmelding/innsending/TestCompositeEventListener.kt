package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore

class TestCompositeEventListener( // TODO: slette
    // private val event: EventName,
    redisStore: RedisStore
) :
    CompositeEventListener(redisStore) {
    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        TODO("Not yet implemented")
    }

    override fun finalize(message: JsonMessage) {
        TODO("Not yet implemented")
    }

    override fun terminate(message: JsonMessage) {
        TODO("Not yet implemented")
    }

    override fun initialTransactionState(message: JsonMessage) {
        TODO("Not yet implemented")
    }
}
