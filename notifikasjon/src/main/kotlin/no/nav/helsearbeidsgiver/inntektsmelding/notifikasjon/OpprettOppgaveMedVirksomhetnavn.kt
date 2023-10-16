package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.IRedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey

class OpprettOppgaveMedVirksomhetnavn(
    private val rapidsConnection: RapidsConnection,
    override val redisStore: IRedisStore
) : CompositeEventListener(redisStore) {

    override val event: EventName = EventName.FORESPØRSEL_LAGRET

    init {
        withEventListener {
            StatefullEventListener(
                redisStore,
                event,
                arrayOf(DataFelt.ORGNRUNDERENHET.str, Key.FORESPOERSEL_ID.str, Key.UUID.str),
                this,
                rapidsConnection
            )
        }
        withDataKanal {
            StatefullDataKanal(
                arrayOf(DataFelt.VIRKSOMHET.str),
                event,
                this,
                rapidsConnection,
                redisStore
            )
        }
        withFailKanal { DelegatingFailKanal(event, this, rapidsConnection) }
    }
    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        val transaksjonsId = message[Key.UUID.str].asText()
        val forespørselId = redisStore.get(transaksjonsId + Key.FORESPOERSEL_ID.str)!!
        if (transaction == Transaction.NEW) {
            rapidsConnection.publish(
                JsonMessage.newMessage(
                    mapOf(
                        Key.EVENT_NAME.str to event.name,
                        Key.UUID.str to transaksjonsId,
                        Key.BEHOV.str to BehovType.VIRKSOMHET.name,
                        Key.FORESPOERSEL_ID.str to forespørselId,
                        DataFelt.ORGNRUNDERENHET.str to message[DataFelt.ORGNRUNDERENHET.str]
                    )
                ).toJson()
            )
        }
    }

    override fun finalize(message: JsonMessage) {
        val transaksjonsId = message[Key.UUID.str].asText()
        val virksomhetnavn = redisStore.get(RedisKey.of(transaksjonsId, DataFelt.VIRKSOMHET))
        val orgnr = redisStore.get(RedisKey.of(transaksjonsId, DataFelt.ORGNRUNDERENHET))
        val forespoerselId = message[Key.FORESPOERSEL_ID.str].asText()
        rapidsConnection.publish(
            JsonMessage.newMessage(
                mapOf(
                    Key.BEHOV.str to BehovType.OPPRETT_OPPGAVE,
                    Key.FORESPOERSEL_ID.str to forespoerselId,
                    DataFelt.VIRKSOMHET.str to virksomhetnavn!!,
                    DataFelt.ORGNRUNDERENHET.str to orgnr!!
                )
            ).toJson()
        )
    }

    override fun terminate(message: JsonMessage) {
        redisStore.set(message[Key.UUID.str].asText(), message[Key.FAIL.str].asText())
    }

    override fun onError(feil: Fail): Transaction {
        if (feil.behov == BehovType.VIRKSOMHET) {
            val virksomhetKey = "${feil.uuid}${DataFelt.VIRKSOMHET.str}"
            redisStore.set(virksomhetKey, " ")
            return Transaction.FINALIZE
        }
        return Transaction.TERMINATE
    }
}
