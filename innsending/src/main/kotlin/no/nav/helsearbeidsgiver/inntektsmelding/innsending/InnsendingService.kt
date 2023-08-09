package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.IRedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.utils.log.logger

class InnsendingService(
    private val rapidsConnection: RapidsConnection,
    override val redisStore: IRedisStore
) : CompositeEventListener(redisStore) {

    override val event: EventName = EventName.INSENDING_STARTED

    private val logger = logger()

    init {
        withFailKanal { DelegatingFailKanal(event, it, rapidsConnection) }
        withDataKanal {
            StatefullDataKanal(
                arrayOf(
                    DataFelt.VIRKSOMHET.str,
                    DataFelt.ARBEIDSFORHOLD.str,
                    DataFelt.INNTEKTSMELDING_DOKUMENT.str,
                    DataFelt.ARBEIDSTAKER_INFORMASJON.str
                ),
                event,
                it,
                rapidsConnection,
                redisStore
            )
        }
        withEventListener {
            StatefullEventListener(
                redisStore,
                event,
                arrayOf(DataFelt.FORESPOERSEL_ID.str, DataFelt.ORGNRUNDERENHET.str, DataFelt.INNTEKTSMELDING.str, Key.IDENTITETSNUMMER.str),
                it,
                rapidsConnection
            )
        }
    }

    override fun onError(feil: Fail): Transaction {
        if (feil.behov == BehovType.VIRKSOMHET) {
            val virksomhetKey = "${feil.uuid}${DataFelt.VIRKSOMHET}"
            redisStore.set(virksomhetKey, "Ukjent virksomhet")
            return Transaction.IN_PROGRESS
        } else if (feil.behov == BehovType.FULLT_NAVN) {
            val fulltNavnKey = "${feil.uuid}${DataFelt.ARBEIDSTAKER_INFORMASJON.str}"
            redisStore.set(fulltNavnKey, customObjectMapper().writeValueAsString(PersonDato("Ukjent person", null)))
            return Transaction.IN_PROGRESS
        }
        return Transaction.TERMINATE
    }

    override fun terminate(message: JsonMessage) {
        redisStore.set(message[Key.UUID.str].asText(), message[Key.FAIL.str].asText())
    }

    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        val uuid: String = message[Key.UUID.str].asText()
        when (transaction) {
            Transaction.NEW -> {
                logger.info("InnsendingService: emitting behov Virksomhet")
                rapidsConnection.publish(
                    JsonMessage.newMessage(
                        mapOf(
                            Key.EVENT_NAME.str to event.name,
                            Key.BEHOV.str to BehovType.VIRKSOMHET.name,
                            DataFelt.ORGNRUNDERENHET.str to message[DataFelt.ORGNRUNDERENHET.str].asText(),
                            Key.UUID.str to uuid
                        )
                    ).toJson()
                )
                logger.info("InnsendingService: emitting behov ARBEIDSFORHOLD")
                rapidsConnection.publish(
                    JsonMessage.newMessage(
                        mapOf(
                            Key.EVENT_NAME.str to event.name,
                            Key.BEHOV.str to BehovType.ARBEIDSFORHOLD.name,
                            Key.IDENTITETSNUMMER.str to message[Key.IDENTITETSNUMMER.str].asText(),
                            Key.UUID.str to uuid
                        )
                    ).toJson()
                )
                logger.info("InnsendingService: emitting behov FULLT_NAVN")
                rapidsConnection.publish(
                    JsonMessage.newMessage(
                        mapOf(
                            Key.EVENT_NAME.str to event.name,
                            Key.BEHOV.str to BehovType.FULLT_NAVN.name,
                            Key.IDENTITETSNUMMER.str to message[Key.IDENTITETSNUMMER.str].asText(),
                            Key.UUID.str to uuid
                        )
                    ).toJson()
                )
            }

            Transaction.IN_PROGRESS -> {
                if (isDataCollected(*step1data(message[Key.UUID.str].asText()))) {
                    val arbeidstakerRedis = redisStore.get(RedisKey.of(uuid, DataFelt.ARBEIDSTAKER_INFORMASJON), PersonDato::class.java)
                    logger.info("InnsendingService: emitting behov PERSISTER_IM")
                    rapidsConnection.publish(
                        JsonMessage.newMessage(
                            mapOf(
                                Key.EVENT_NAME.str to event.name,
                                Key.BEHOV.str to BehovType.PERSISTER_IM.name,
                                DataFelt.VIRKSOMHET.str to (redisStore.get(RedisKey.of(uuid, DataFelt.VIRKSOMHET)) ?: "Ukjent virksomhet"),
                                DataFelt.ARBEIDSTAKER_INFORMASJON.str to (
                                    arbeidstakerRedis ?: PersonDato(
                                        "Ukjent navn",
                                        null
                                    )
                                    ),
                                DataFelt.INNTEKTSMELDING.str to customObjectMapper().readTree(redisStore.get(RedisKey.of(uuid, DataFelt.INNTEKTSMELDING)))!!,
                                Key.FORESPOERSEL_ID.str to redisStore.get(RedisKey.of(uuid, DataFelt.FORESPOERSEL_ID))!!,
                                Key.UUID.str to uuid
                            )
                        ).toJson()
                    )
                }
            }

            else -> {
                logger.error("Illegal transaction type ecountered in dispatchBehov $transaction for uuid= $uuid")
            }
        }
    }

    override fun finalize(message: JsonMessage) {
        val uuid: String = message[Key.UUID.str].asText()
        val clientId = redisStore.get(RedisKey.of(uuid, event))
        logger.info("publiserer under clientID $clientId")
        redisStore.set(RedisKey.of(clientId!!), redisStore.get(RedisKey.of(uuid, DataFelt.INNTEKTSMELDING_DOKUMENT))!!)
        logger.info("Publiserer INNTEKTSMELDING_DOKUMENT under uuid $uuid")
        logger.info("InnsendingService: emitting event INNTEKTSMELDING_MOTTATT")
        rapidsConnection.publish(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_MOTTATT,
                    DataFelt.INNTEKTSMELDING_DOKUMENT.str to message[DataFelt.INNTEKTSMELDING_DOKUMENT.str],
                    Key.TRANSACTION_ORIGIN.str to uuid,
                    DataFelt.FORESPOERSEL_ID.str to redisStore.get(RedisKey.of(uuid, DataFelt.FORESPOERSEL_ID))!!
                )
            ).toJson().also {
                sikkerLogger.info("Submitting INNTEKTSMELDING_MOTTATT $it")
                logger.info("Submitting INNTEKTSMELDING_MOTTATT")
            }
        )
    }

    private fun step1data(uuid: String): Array<RedisKey> = arrayOf(
        RedisKey.of(uuid, DataFelt.VIRKSOMHET),
        RedisKey.of(uuid, DataFelt.ARBEIDSFORHOLD),
        RedisKey.of(uuid, DataFelt.ARBEIDSTAKER_INFORMASJON)
    )
}
