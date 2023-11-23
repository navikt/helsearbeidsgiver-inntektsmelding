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
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import java.util.UUID

class OpprettOppgaveService(
    private val rapidsConnection: RapidsConnection,
    override val redisStore: RedisStore
) : CompositeEventListener(redisStore) {

    override val event: EventName = EventName.OPPGAVE_OPPRETT_REQUESTED

    init {
        withEventListener {
            StatefullEventListener(
                redisStore,
                event,
                arrayOf(DataFelt.ORGNRUNDERENHET, Key.FORESPOERSEL_ID, Key.UUID),
                this,
                rapidsConnection
            )
        }
        withDataKanal {
            StatefullDataKanal(
                arrayOf(DataFelt.VIRKSOMHET),
                event,
                this,
                rapidsConnection,
                redisStore
            )
        }
        withFailKanal { DelegatingFailKanal(event, this, rapidsConnection) }
    }
    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        val transaksjonsId = message[Key.UUID.str].asText().let(UUID::fromString)
        val forespoerselId = redisStore.get(RedisKey.of(transaksjonsId, Key.FORESPOERSEL_ID))!!
        if (transaction == Transaction.NEW) {
            rapidsConnection.publish(
                JsonMessage.newMessage(
                    mapOf(
                        Key.EVENT_NAME.str to event.name,
                        Key.UUID.str to transaksjonsId,
                        Key.BEHOV.str to BehovType.VIRKSOMHET.name,
                        Key.FORESPOERSEL_ID.str to forespoerselId,
                        DataFelt.ORGNRUNDERENHET.str to message[DataFelt.ORGNRUNDERENHET.str]
                    )
                ).toJson()
            )
        }
    }

    override fun finalize(message: JsonMessage) {
        val transaksjonsId = message[Key.UUID.str].asText().let(UUID::fromString)
        val virksomhetnavn = redisStore.get(RedisKey.of(transaksjonsId, DataFelt.VIRKSOMHET))
        val orgnr = redisStore.get(RedisKey.of(transaksjonsId, DataFelt.ORGNRUNDERENHET))
        val forespoerselId = message[Key.FORESPOERSEL_ID.str].asText()
        rapidsConnection.publish(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to event.name,
                    Key.BEHOV.str to BehovType.OPPRETT_OPPGAVE,
                    Key.UUID.str to transaksjonsId,
                    Key.FORESPOERSEL_ID.str to forespoerselId,
                    DataFelt.VIRKSOMHET.str to virksomhetnavn!!,
                    DataFelt.ORGNRUNDERENHET.str to orgnr!!
                )
            ).toJson()
        )
    }

    override fun terminate(fail: Fail) {
        val transaksjonId = fail.uuid!!.let(UUID::fromString)

        val clientId = redisStore.get(RedisKey.of(transaksjonId, event))
            ?.let(UUID::fromString)

        if (clientId == null) {
            MdcUtils.withLogFields(
                Log.transaksjonId(transaksjonId)
            ) {
                sikkerLogger.error("Forsøkte å terminere, men clientId mangler i Redis. forespoerselId=${fail.forespørselId}")
            }
        } else {
            redisStore.set(RedisKey.of(clientId), fail.feilmelding)
        }
    }

    override fun onError(feil: Fail): Transaction {
        if (feil.behov == BehovType.VIRKSOMHET) {
            val virksomhetKey = RedisKey.of(feil.uuid!!.let(UUID::fromString), DataFelt.VIRKSOMHET)
            redisStore.set(virksomhetKey, "Arbeidsgiver")
            return Transaction.FINALIZE
        }
        return Transaction.TERMINATE
    }
}
