package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import java.util.UUID

class OpprettSakService(private val rapidsConnection: RapidsConnection, override val redisStore: RedisStore) : CompositeEventListener(redisStore) {
    override val event: EventName = EventName.SAK_OPPRETT_REQUESTED

    init {
        withEventListener {
            StatefullEventListener(
                redisStore,
                event,
                arrayOf(Key.ORGNRUNDERENHET, Key.IDENTITETSNUMMER, Key.FORESPOERSEL_ID, Key.UUID),
                this,
                rapidsConnection
            )
        }
        withDataKanal {
            StatefullDataKanal(
                arrayOf(Key.ARBEIDSTAKER_INFORMASJON, Key.SAK_ID, Key.PERSISTERT_SAK_ID),
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
                        Key.BEHOV.str to BehovType.FULLT_NAVN.name,
                        Key.IDENTITETSNUMMER.str to redisStore.get(RedisKey.of(transaksjonsId, Key.IDENTITETSNUMMER))!!,
                        Key.FORESPOERSEL_ID.str to forespoerselId

                    )
                ).toJson()
            )
        } else if (transaction == Transaction.IN_PROGRESS) {
            if (isDataCollected(*steg3(transaksjonsId))) {
                rapidsConnection.publish(
                    JsonMessage.newMessage(
                        mapOf(
                            Key.EVENT_NAME.str to event.name,
                            Key.UUID.str to transaksjonsId,
                            Key.BEHOV.str to BehovType.PERSISTER_SAK_ID.name,
                            Key.FORESPOERSEL_ID.str to forespoerselId,
                            Key.SAK_ID.str to redisStore.get(RedisKey.of(transaksjonsId, Key.SAK_ID))!!
                        )
                    ).toJson()
                )
            } else if (isDataCollected(*steg2(transaksjonsId))) {
                val arbeidstakerRedis = redisStore.get(RedisKey.of(transaksjonsId, Key.ARBEIDSTAKER_INFORMASJON))?.fromJson(PersonDato.serializer())
                rapidsConnection.publish(
                    JsonMessage.newMessage(
                        mapOf(
                            Key.EVENT_NAME.str to event.name,
                            Key.UUID.str to transaksjonsId,
                            Key.BEHOV.str to BehovType.OPPRETT_SAK,
                            Key.FORESPOERSEL_ID.str to forespoerselId,
                            Key.ORGNRUNDERENHET.str to redisStore.get(RedisKey.of(transaksjonsId, Key.ORGNRUNDERENHET))!!,
                            // @TODO this transformation is not nessesary. StatefullDataKanal should be fixed to use Tree
                            Key.ARBEIDSTAKER_INFORMASJON.str to arbeidstakerRedis!!
                        )
                    ).toJson()
                )
            }
        }
    }

    override fun finalize(message: JsonMessage) {
        val transaksjonsId = message[Key.UUID.str].asText().let(UUID::fromString)
        rapidsConnection.publish(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.SAK_OPPRETTET.name,
                    Key.FORESPOERSEL_ID.str to message[Key.FORESPOERSEL_ID.str],
                    Key.SAK_ID.str to redisStore.get(RedisKey.of(transaksjonsId, Key.SAK_ID))!!
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
        if (feil.behov == BehovType.FULLT_NAVN) {
            val fulltNavnKey = RedisKey.of(feil.uuid!!.let(UUID::fromString), Key.ARBEIDSTAKER_INFORMASJON)
            redisStore.set(fulltNavnKey, PersonDato("Ukjent person", null, "").toJsonStr(PersonDato.serializer()))
            return Transaction.IN_PROGRESS
        }
        return Transaction.TERMINATE
    }

    fun steg2(transactionId: UUID) = arrayOf(RedisKey.of(transactionId, Key.ARBEIDSTAKER_INFORMASJON))
    fun steg3(transactionId: UUID) = arrayOf(RedisKey.of(transactionId, Key.SAK_ID))
}
