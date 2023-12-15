package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class OpprettSakService(private val rapidsConnection: RapidsConnection, override val redisStore: RedisStore) : CompositeEventListener(redisStore) {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

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
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(EventName.SAK_OPPRETT_REQUESTED)
        ) {
            val json = message.toJson().parseJson().toMap()

            val transaksjonsId = json[Key.UUID]?.fromJson(UuidSerializer)
            if (transaksjonsId == null) {
                "Mangler transaksjonId. Klarer ikke opprette sak.".also {
                    logger.error(it)
                    sikkerLogger.error(it)
                }
                return
            }

            val forespoerselId = redisStore.get(RedisKey.of(transaksjonsId, Key.FORESPOERSEL_ID))?.let(UUID::fromString)
                ?: json[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer)

            if (forespoerselId == null) {
                MdcUtils.withLogFields(
                    Log.transaksjonId(transaksjonsId)
                ) {
                    "Mangler forespoerselId. Klarer ikke opprette sak.".also {
                        logger.error(it)
                        sikkerLogger.error(it)
                    }
                }
                return
            }

            MdcUtils.withLogFields(
                Log.transaksjonId(transaksjonsId),
                Log.forespoerselId(forespoerselId)
            ) {
                dispatch(transaction, transaksjonsId, forespoerselId)
            }
        }
    }

    private fun dispatch(transaction: Transaction, transaksjonsId: UUID, forespoerselId: UUID) {
        if (transaction == Transaction.NEW) {
            val fnr = redisStore.get(RedisKey.of(transaksjonsId, Key.IDENTITETSNUMMER))
            if (fnr == null) {
                "Mangler fnr i redis. Klarer ikke opprette sak.".also {
                    logger.error(it)
                    sikkerLogger.error(it)
                }
                return
            }

            rapidsConnection.publish(
                JsonMessage.newMessage(
                    mapOf(
                        Key.EVENT_NAME.str to event.name,
                        Key.UUID.str to transaksjonsId,
                        Key.BEHOV.str to BehovType.FULLT_NAVN.name,
                        Key.IDENTITETSNUMMER.str to fnr,
                        Key.FORESPOERSEL_ID.str to forespoerselId

                    )
                ).toJson()
            )
        } else if (transaction == Transaction.IN_PROGRESS) {
            if (isDataCollected(*steg3(transaksjonsId))) {
                val sakId = redisStore.get(RedisKey.of(transaksjonsId, Key.SAK_ID))
                if (sakId == null) {
                    "Mangler sakId i redis. Klarer ikke opprette sak.".also {
                        logger.error(it)
                        sikkerLogger.error(it)
                    }
                    return
                }

                rapidsConnection.publish(
                    JsonMessage.newMessage(
                        mapOf(
                            Key.EVENT_NAME.str to event.name,
                            Key.UUID.str to transaksjonsId,
                            Key.BEHOV.str to BehovType.PERSISTER_SAK_ID.name,
                            Key.FORESPOERSEL_ID.str to forespoerselId,
                            Key.SAK_ID.str to sakId
                        )
                    ).toJson()
                )
            } else if (isDataCollected(*steg2(transaksjonsId))) {
                val orgnr = redisStore.get(RedisKey.of(transaksjonsId, Key.ORGNRUNDERENHET))
                if (orgnr == null) {
                    "Mangler orgnr i redis. Klarer ikke opprette sak.".also {
                        logger.error(it)
                        sikkerLogger.error(it)
                    }
                    return
                }

                val arbeidstaker = redisStore.get(RedisKey.of(transaksjonsId, Key.ARBEIDSTAKER_INFORMASJON))
                    ?.fromJson(PersonDato.serializer())
                    ?: ukjentArbeidstaker()

                rapidsConnection.publish(
                    JsonMessage.newMessage(
                        mapOf(
                            Key.EVENT_NAME.str to event.name,
                            Key.UUID.str to transaksjonsId,
                            Key.BEHOV.str to BehovType.OPPRETT_SAK,
                            Key.FORESPOERSEL_ID.str to forespoerselId,
                            Key.ORGNRUNDERENHET.str to orgnr,
                            Key.ARBEIDSTAKER_INFORMASJON.str to arbeidstaker
                        )
                    ).toJson()
                )
            }
        }
    }

    override fun finalize(message: JsonMessage) {
        val transaksjonsId = message[Key.UUID.str].asText().let(UUID::fromString)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(EventName.SAK_OPPRETT_REQUESTED),
            Log.transaksjonId(transaksjonsId)
        ) {
            val sakId = redisStore.get(RedisKey.of(transaksjonsId, Key.SAK_ID))
            if (sakId == null) {
                "Mangler sakId i redis. Klarer ikke publisere event om opprettet sak.".also {
                    logger.error(it)
                    sikkerLogger.error(it)
                }
                return
            }

            rapidsConnection.publish(
                JsonMessage.newMessage(
                    mapOf(
                        Key.EVENT_NAME.str to EventName.SAK_OPPRETTET.name,
                        Key.FORESPOERSEL_ID.str to message[Key.FORESPOERSEL_ID.str],
                        Key.SAK_ID.str to sakId
                    )
                ).toJson()
            )
        }
    }

    override fun terminate(fail: Fail) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(EventName.SAK_OPPRETT_REQUESTED),
            Log.transaksjonId(fail.transaksjonId)
        ) {
            val clientId = redisStore.get(RedisKey.of(fail.transaksjonId, event))
                ?.let(UUID::fromString)

            if (clientId == null) {
                sikkerLogger.error("Forsøkte å terminere, men clientId mangler i Redis. forespoerselId=${fail.forespoerselId}")
            } else {
                redisStore.set(RedisKey.of(clientId), fail.feilmelding)
            }
        }
    }

    override fun onError(feil: Fail): Transaction {
        val utloesendeBehov = Key.BEHOV.lesOrNull(BehovType.serializer(), feil.utloesendeMelding.toMap())
        if (utloesendeBehov == BehovType.FULLT_NAVN) {
            val arbeidstakerKey = RedisKey.of(feil.transaksjonId, Key.ARBEIDSTAKER_INFORMASJON)
            redisStore.set(arbeidstakerKey, ukjentArbeidstaker().toJsonStr(PersonDato.serializer()))
            return Transaction.IN_PROGRESS
        }
        return Transaction.TERMINATE
    }

    private fun ukjentArbeidstaker(): PersonDato =
        PersonDato("Ukjent person", null, "")

    private fun steg2(transactionId: UUID) = arrayOf(RedisKey.of(transactionId, Key.ARBEIDSTAKER_INFORMASJON))
    private fun steg3(transactionId: UUID) = arrayOf(RedisKey.of(transactionId, Key.SAK_ID))
}
