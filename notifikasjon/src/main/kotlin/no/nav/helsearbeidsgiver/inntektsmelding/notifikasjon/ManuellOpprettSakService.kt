package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.IRedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr

class ManuellOpprettSakService(private val rapidsConnection: RapidsConnection, override val redisStore: IRedisStore) : CompositeEventListener(redisStore) {
    override val event: EventName = EventName.MANUELL_OPPRETT_SAK_REQUESTED

    init {
        withEventListener {
            StatefullEventListener(
                redisStore,
                event,
                arrayOf(Key.FORESPOERSEL_ID.str, Key.UUID.str),
                this,
                rapidsConnection
            )
        }
        withDataKanal {
            StatefullDataKanal(
                arrayOf(
                    DataFelt.FORESPOERSEL_SVAR.str,
                    DataFelt.ARBEIDSTAKER_INFORMASJON.str,
                    DataFelt.SAK_ID.str,
                    DataFelt.PERSISTERT_SAK_ID.str
                ),
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
                        Key.BEHOV.str to BehovType.HENT_TRENGER_IM.name,
                        Key.FORESPOERSEL_ID.str to forespørselId,
                        Key.UUID.str to transaksjonsId
                    )
                ).toJson()
            )
        } else if (transaction == Transaction.IN_PROGRESS) {
            val forespoersel = redisStore.get(transaksjonsId + DataFelt.FORESPOERSEL_SVAR.str)?.fromJson(TrengerInntekt.serializer())

            if (isDataCollected(*steg4(transaksjonsId))) {
                rapidsConnection.publish(
                    JsonMessage.newMessage(
                        mapOf(
                            Key.EVENT_NAME.str to event.name,
                            Key.UUID.str to transaksjonsId,
                            Key.BEHOV.str to BehovType.PERSISTER_SAK_ID.name,
                            Key.FORESPOERSEL_ID.str to forespørselId,
                            DataFelt.SAK_ID.str to redisStore.get(RedisKey.of(transaksjonsId, DataFelt.SAK_ID))!!
                        )
                    ).toJson()
                )
            } else if (isDataCollected(*steg3(transaksjonsId))) {
                val arbeidstakerRedis = redisStore.get(RedisKey.of(transaksjonsId, DataFelt.ARBEIDSTAKER_INFORMASJON), PersonDato::class.java)
                rapidsConnection.publish(
                    JsonMessage.newMessage(
                        mapOf(
                            Key.EVENT_NAME.str to event.name,
                            Key.UUID.str to transaksjonsId,
                            Key.BEHOV.str to BehovType.OPPRETT_SAK,
                            Key.FORESPOERSEL_ID.str to forespørselId,
                            DataFelt.ORGNRUNDERENHET.str to forespoersel!!.orgnr,
                            // @TODO this transformation is not nessesary. StatefullDataKanal should be fixed to use Tree
                            DataFelt.ARBEIDSTAKER_INFORMASJON.str to arbeidstakerRedis!!
                        )
                    ).toJson()
                )
            } else if (isDataCollected(*steg2(transaksjonsId))) {
                rapidsConnection.publish(
                    JsonMessage.newMessage(
                        mapOf(
                            Key.EVENT_NAME.str to event.name,
                            Key.UUID.str to transaksjonsId,
                            Key.BEHOV.str to BehovType.FULLT_NAVN.name,
                            Key.IDENTITETSNUMMER.str to forespoersel!!.fnr,
                            Key.FORESPOERSEL_ID.str to forespørselId

                        )
                    ).toJson()
                )
            }
        }
    }

    override fun finalize(message: JsonMessage) {
        val transaksjonsId = message[Key.UUID.str].asText()
        rapidsConnection.publish(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.SAK_OPPRETTET.name,
                    Key.FORESPOERSEL_ID.str to message[Key.FORESPOERSEL_ID.str],
                    DataFelt.SAK_ID.str to redisStore.get(RedisKey.of(transaksjonsId, DataFelt.SAK_ID))!!
                )
            ).toJson()
        )
    }

    override fun terminate(message: JsonMessage) {
        redisStore.set(message[Key.UUID.str].asText(), message[Key.FAIL.str].asText())
    }

    override fun onError(feil: Fail): Transaction {
        if (feil.behov == BehovType.FULLT_NAVN) {
            val fulltNavnKey = "${feil.uuid}${DataFelt.ARBEIDSTAKER_INFORMASJON.str}"
            redisStore.set(fulltNavnKey, PersonDato("Ukjent person", null, "").toJsonStr(PersonDato.serializer()))
            return Transaction.IN_PROGRESS
        }
        return Transaction.TERMINATE
    }

    fun steg2(transactionId: String) = arrayOf(RedisKey.of(transactionId, DataFelt.FORESPOERSEL_SVAR))
    fun steg3(transactionId: String) = arrayOf(RedisKey.of(transactionId, DataFelt.ARBEIDSTAKER_INFORMASJON))
    fun steg4(transactionId: String) = arrayOf(RedisKey.of(transactionId, DataFelt.SAK_ID))
}
