package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.EventName

class TestCompositeEventListener(
    rapidsConnection: RapidsConnection,
    override val event: EventName,
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
