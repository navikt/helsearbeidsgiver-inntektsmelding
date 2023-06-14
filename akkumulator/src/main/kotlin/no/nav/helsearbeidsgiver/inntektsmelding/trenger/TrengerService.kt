package no.nav.helsearbeidsgiver.inntektsmelding.trenger

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.IRedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.utils.json.toJson

class TrengerService(private val rapidsConnection: RapidsConnection, override val redisStore: IRedisStore) : CompositeEventListener(redisStore) {

    override val event: EventName = EventName.TRENGER_REQUESTED

    init {
        withFailKanal { DelegatingFailKanal(event, it, rapidsConnection) }
        withDataKanal { StatefullDataKanal(listOf(DataFelt.FORESPOERSEL_SVAR.str, "no-finalize").toTypedArray(), event, it, rapidsConnection, redisStore) }
        withEventListener { StatefullEventListener(redisStore, event, listOf(DataFelt.FORESPOERSEL_ID.str).toTypedArray(), it, rapidsConnection) }
    }

    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        val uuid = message[Key.UUID.str].asText()
        if (transaction == Transaction.NEW) {
            rapidsConnection.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                Key.UUID to uuid.toJson(),
                DataFelt.FORESPOERSEL_ID to redisStore.get(RedisKey.of(uuid, DataFelt.FORESPOERSEL_ID))!!.toJson(),
                Key.BOOMERANG to mapOf(
                    Key.NESTE_BEHOV.str to listOf(BehovType.PREUTFYLL).toJson(BehovType.serializer()),
                    Key.INITIATE_ID.str to uuid.toJson(),
                    Key.INITIATE_EVENT.str to EventName.TRENGER_REQUESTED.toJson()
                ).toJson()
            )
        }
        else if (transaction == Transaction.IN_PROGRESS) {
            /*
            BehovType.VIRKSOMHET.name,
            BehovType.FULLT_NAVN.name,
            BehovType.INNTEKT.name,
            BehovType.ARBEIDSFORHOLD.name
             */
            rapidsConnection.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.VIRKSOMHET.toJson()
            )
            rapidsConnection.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.FULLT_NAVN.toJson()
            )
            rapidsConnection.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.INNTEKT.toJson()
            )
            rapidsConnection.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.ARBEIDSFORHOLD.toJson()
            )

            println("Heeeelllllloooooooooooooo!!!!!")
        }
    }

    override fun finalize(message: JsonMessage) {
    }

    override fun terminate(message: JsonMessage) {
    }
}
