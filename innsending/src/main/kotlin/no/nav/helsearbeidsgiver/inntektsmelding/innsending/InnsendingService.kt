package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.IRedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.logger

class InnsendingService(
    private val rapid: RapidsConnection,
    override val redisStore: IRedisStore
) : CompositeEventListener(redisStore) {

    override val event: EventName = EventName.INSENDING_STARTED

    private val logger = logger()

    init {
        withFailKanal { DelegatingFailKanal(event, it, rapid) }
        withDataKanal {
            StatefullDataKanal(
                dataFelter = arrayOf(
                    DataFelt.VIRKSOMHET.str,
                    DataFelt.ARBEIDSFORHOLD.str,
                    DataFelt.INNTEKTSMELDING_DOKUMENT.str,
                    DataFelt.ARBEIDSGIVER_INFORMASJON.str,
                    DataFelt.ARBEIDSTAKER_INFORMASJON.str,
                    //DataFelt.ER_DUPLIKAT_IM.str
                ),
                event,
                it,
                rapid,
                redisStore
            )
        }
        withEventListener {
            StatefullEventListener(
                redisStore,
                event,
                arrayOf(
                    DataFelt.FORESPOERSEL_ID.str,
                    DataFelt.ORGNRUNDERENHET.str,
                    DataFelt.INNTEKTSMELDING.str,
                    Key.ARBEIDSGIVER_ID.str,
                    Key.IDENTITETSNUMMER.str
                ),
                it,
                rapid
            )
        }
    }

    override fun onError(feil: Fail): Transaction {
        if (feil.behov == BehovType.VIRKSOMHET) {
            val virksomhetKey = "${feil.uuid}${DataFelt.VIRKSOMHET}"
            redisStore.set(virksomhetKey, "Ukjent virksomhet")
            return Transaction.IN_PROGRESS
        } else if (feil.behov == BehovType.FULLT_NAVN) {
            val arbeidstakerFulltnavnKey = "${feil.uuid}${DataFelt.ARBEIDSTAKER_INFORMASJON.str}"
            val arbeidsgiverFulltnavnKey = "${feil.uuid}${DataFelt.ARBEIDSGIVER_INFORMASJON.str}"
            redisStore.set(arbeidstakerFulltnavnKey, personIkkeFunnet().toJsonStr(PersonDato.serializer()))
            redisStore.set(arbeidsgiverFulltnavnKey, personIkkeFunnet().toJsonStr(PersonDato.serializer()))
            return Transaction.IN_PROGRESS
        } else if (feil.behov == BehovType.PERSISTER_IM) {
            logger.error("InnsendingService: feil ved persistering av inntektsmelding: ${feil.feilmelding}")
            return Transaction.TERMINATE
        }
        return Transaction.TERMINATE
    }

    override fun terminate(message: JsonMessage) {
        val uuid: String = message[Key.UUID.str].asText()
        val clientId = redisStore.get(RedisKey.of(uuid, event))
        redisStore.set(clientId!!, message[Key.FAIL.str].asText())
    }

    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        val uuid: String = message[Key.UUID.str].asText()
        when (transaction) {
            Transaction.NEW -> {
                logger.info("InnsendingService: emitting behov Virksomhet")
                rapid.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
                    DataFelt.ORGNRUNDERENHET to message[DataFelt.ORGNRUNDERENHET.str].asText().toJson(),
                    Key.UUID to uuid.toJson()
                )

                logger.info("InnsendingService: emitting behov ARBEIDSFORHOLD")
                rapid.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to BehovType.ARBEIDSFORHOLD.toJson(),
                    Key.IDENTITETSNUMMER to message[Key.IDENTITETSNUMMER.str].asText().toJson(),
                    Key.UUID to uuid.toJson()
                )

                logger.info("InnsendingService: emitting behov FULLT_NAVN")
                rapid.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to BehovType.FULLT_NAVN.toJson(),
                    Key.IDENTITETSNUMMER to message[Key.IDENTITETSNUMMER.str].asText().toJson(),
                    Key.ARBEIDSGIVER_ID to message[Key.ARBEIDSGIVER_ID.str].asText().toJson(),
                    Key.UUID to uuid.toJson()
                )
            }

            Transaction.IN_PROGRESS -> {
                if (isDataCollected(*step1data(message[Key.UUID.str].asText()))) {
                    val arbeidstakerRedis = redisStore.get(RedisKey.of(uuid, DataFelt.ARBEIDSTAKER_INFORMASJON), PersonDato::class.java)
                    val arbeidsgiverRedis = redisStore.get(RedisKey.of(uuid, DataFelt.ARBEIDSGIVER_INFORMASJON), PersonDato::class.java)
                    logger.info("InnsendingService: emitting behov PERSISTER_IM")
                    rapid.publish(
                        Key.EVENT_NAME to event.toJson(),
                        Key.BEHOV to BehovType.PERSISTER_IM.toJson(),
                        DataFelt.VIRKSOMHET to (redisStore.get(RedisKey.of(uuid, DataFelt.VIRKSOMHET)) ?: "Ukjent virksomhet").toJson(),
                        DataFelt.ARBEIDSTAKER_INFORMASJON to (
                            arbeidstakerRedis ?: personIkkeFunnet(message[Key.IDENTITETSNUMMER.str].asText())
                            ).toJson(PersonDato.serializer()),
                        DataFelt.ARBEIDSGIVER_INFORMASJON to (
                            arbeidsgiverRedis ?: personIkkeFunnet(message[Key.ARBEIDSGIVER_ID.str].asText())
                            ).toJson(PersonDato.serializer()),
                        DataFelt.INNTEKTSMELDING to redisStore.get(RedisKey.of(uuid, DataFelt.INNTEKTSMELDING))!!.parseJson(),
                        Key.FORESPOERSEL_ID to redisStore.get(RedisKey.of(uuid, DataFelt.FORESPOERSEL_ID))!!.toJson(),
                        Key.UUID to uuid.toJson()
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
        val erDuplikat = message[DataFelt.ER_DUPLIKAT_IM.str].asBoolean()
        if (!erDuplikat) {
            logger.info("Publiserer INNTEKTSMELDING_DOKUMENT under uuid $uuid")
            logger.info("InnsendingService: emitting event INNTEKTSMELDING_MOTTATT")
            rapid.publish(
                Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
                Key.UUID to uuid.toJson(),
                DataFelt.FORESPOERSEL_ID to redisStore.get(RedisKey.of(uuid, DataFelt.FORESPOERSEL_ID))!!.toJson(),
                DataFelt.INNTEKTSMELDING_DOKUMENT to message[DataFelt.INNTEKTSMELDING_DOKUMENT.str].toJsonElement()
            )
                .also {
                    logger.info("Submitting INNTEKTSMELDING_MOTTATT")
                    sikkerLogger.info("Submitting INNTEKTSMELDING_MOTTATT ${it.toPretty()}")
                }
        }
    }

    private fun step1data(uuid: String): Array<RedisKey> = arrayOf(
        RedisKey.of(uuid, DataFelt.VIRKSOMHET),
        RedisKey.of(uuid, DataFelt.ARBEIDSFORHOLD),
        RedisKey.of(uuid, DataFelt.ARBEIDSTAKER_INFORMASJON),
        RedisKey.of(uuid, DataFelt.ARBEIDSGIVER_INFORMASJON)
    )

    private fun personIkkeFunnet(ident: String = "") = PersonDato(
        navn = "",
        fødselsdato = null,
        ident = ident
    )
}
