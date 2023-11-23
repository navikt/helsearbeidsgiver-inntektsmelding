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
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class ManuellOpprettSakService(private val rapidsConnection: RapidsConnection, override val redisStore: RedisStore) : CompositeEventListener(redisStore) {
    override val event: EventName = EventName.MANUELL_OPPRETT_SAK_REQUESTED

    private val sikkerLogger = sikkerLogger()

    init {
        withEventListener {
            StatefullEventListener(
                redisStore,
                event,
                arrayOf(Key.FORESPOERSEL_ID, Key.UUID),
                this,
                rapidsConnection
            )
        }
        withDataKanal {
            StatefullDataKanal(
                arrayOf(
                    DataFelt.FORESPOERSEL_SVAR,
                    DataFelt.ARBEIDSTAKER_INFORMASJON,
                    DataFelt.SAK_ID,
                    DataFelt.PERSISTERT_SAK_ID
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
        val transaksjonsId = message[Key.UUID.str].asText().let(UUID::fromString)
        val forespoerselId = redisStore.get(RedisKey.of(transaksjonsId, Key.FORESPOERSEL_ID))!!
        if (transaction == Transaction.NEW) {
            rapidsConnection.publish(
                JsonMessage.newMessage(
                    mapOf(
                        Key.EVENT_NAME.str to event.name,
                        Key.BEHOV.str to BehovType.HENT_TRENGER_IM.name,
                        Key.FORESPOERSEL_ID.str to forespoerselId,
                        Key.UUID.str to transaksjonsId
                    )
                ).toJson()
            )
        } else if (transaction == Transaction.IN_PROGRESS) {
            val forespoersel = redisStore.get(RedisKey.of(transaksjonsId, DataFelt.FORESPOERSEL_SVAR))?.fromJson(TrengerInntekt.serializer())

            if (forespoersel == null) {
                sikkerLogger.error("Fant ikke forespørsel '$forespoerselId' i redis-cache. transaksjonId='$transaksjonsId'")
                return
            }

            when {
                isDataCollected(*steg4(transaksjonsId)) -> {
                    rapidsConnection.publish(
                        JsonMessage.newMessage(
                            mapOf(
                                Key.EVENT_NAME.str to event.name,
                                Key.UUID.str to transaksjonsId,
                                Key.BEHOV.str to BehovType.PERSISTER_SAK_ID.name,
                                Key.FORESPOERSEL_ID.str to forespoerselId,
                                DataFelt.SAK_ID.str to redisStore.get(RedisKey.of(transaksjonsId, DataFelt.SAK_ID))!!
                            )
                        ).toJson()
                    )

                    if (forespoersel.erBesvart) {
                        rapidsConnection.publish(
                            JsonMessage.newMessage(
                                mapOf(
                                    Key.EVENT_NAME.str to EventName.FORESPOERSEL_BESVART,
                                    Key.UUID.str to transaksjonsId,
                                    Key.FORESPOERSEL_ID.str to forespoerselId,
                                    DataFelt.SAK_ID.str to redisStore.get(RedisKey.of(transaksjonsId, DataFelt.SAK_ID))!!
                                )
                            ).toJson()
                        )
                    }
                }
                isDataCollected(*steg3(transaksjonsId)) -> {
                    val arbeidstakerRedis = redisStore.get(RedisKey.of(transaksjonsId, DataFelt.ARBEIDSTAKER_INFORMASJON))?.fromJson(PersonDato.serializer())
                    rapidsConnection.publish(
                        JsonMessage.newMessage(
                            mapOf(
                                Key.EVENT_NAME.str to event.name,
                                Key.UUID.str to transaksjonsId,
                                Key.BEHOV.str to BehovType.OPPRETT_SAK,
                                Key.FORESPOERSEL_ID.str to forespoerselId,
                                DataFelt.ORGNRUNDERENHET.str to forespoersel.orgnr,
                                // @TODO this transformation is not nessesary. StatefullDataKanal should be fixed to use Tree
                                DataFelt.ARBEIDSTAKER_INFORMASJON.str to arbeidstakerRedis!!
                            )
                        ).toJson()
                    )
                }
                isDataCollected(*steg2(transaksjonsId)) -> {
                    rapidsConnection.publish(
                        JsonMessage.newMessage(
                            mapOf(
                                Key.EVENT_NAME.str to event.name,
                                Key.UUID.str to transaksjonsId,
                                Key.BEHOV.str to BehovType.FULLT_NAVN.name,
                                Key.IDENTITETSNUMMER.str to forespoersel.fnr,
                                Key.FORESPOERSEL_ID.str to forespoerselId

                            )
                        ).toJson()
                    )
                }
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
                    DataFelt.SAK_ID.str to redisStore.get(RedisKey.of(transaksjonsId, DataFelt.SAK_ID))!!
                )
            ).toJson()
        )
    }

    override fun terminate(fail: Fail) {
        sikkerLogger.error("Terminerer flyt med transaksjon-ID '${fail.uuid}'")
    }

    override fun onError(feil: Fail): Transaction {
        sikkerLogger.error("Mottok feil:\n$feil")
        return Transaction.TERMINATE
    }

    private fun steg2(transactionId: UUID) = arrayOf(RedisKey.of(transactionId, DataFelt.FORESPOERSEL_SVAR))
    private fun steg3(transactionId: UUID) = arrayOf(RedisKey.of(transactionId, DataFelt.ARBEIDSTAKER_INFORMASJON))
    private fun steg4(transactionId: UUID) = arrayOf(RedisKey.of(transactionId, DataFelt.SAK_ID))
}
