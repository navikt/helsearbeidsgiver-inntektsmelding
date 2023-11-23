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
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import java.util.UUID

class InnsendingService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore
) : CompositeEventListener(redisStore) {

    override val event: EventName = EventName.INSENDING_STARTED

    private val logger = logger()

    init {
        withFailKanal { DelegatingFailKanal(event, it, rapid) }
        withDataKanal {
            StatefullDataKanal(
                dataFelter = arrayOf(
                    DataFelt.VIRKSOMHET,
                    DataFelt.ARBEIDSFORHOLD,
                    DataFelt.INNTEKTSMELDING_DOKUMENT,
                    DataFelt.ARBEIDSGIVER_INFORMASJON,
                    DataFelt.ARBEIDSTAKER_INFORMASJON,
                    DataFelt.ER_DUPLIKAT_IM
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
                    DataFelt.FORESPOERSEL_ID,
                    DataFelt.ORGNRUNDERENHET,
                    DataFelt.INNTEKTSMELDING,
                    Key.ARBEIDSGIVER_ID,
                    Key.IDENTITETSNUMMER
                ),
                it,
                rapid
            )
        }
    }

    override fun onError(feil: Fail): Transaction {
        val transaksjonId = feil.uuid!!.let(UUID::fromString)

        if (feil.behov == BehovType.VIRKSOMHET) {
            val virksomhetKey = RedisKey.of(transaksjonId, DataFelt.VIRKSOMHET)
            redisStore.set(virksomhetKey, "Ukjent virksomhet")
            return Transaction.IN_PROGRESS
        } else if (feil.behov == BehovType.FULLT_NAVN) {
            val arbeidstakerFulltnavnKey = RedisKey.of(transaksjonId, DataFelt.ARBEIDSTAKER_INFORMASJON)
            val arbeidsgiverFulltnavnKey = RedisKey.of(transaksjonId, DataFelt.ARBEIDSGIVER_INFORMASJON)
            redisStore.set(arbeidstakerFulltnavnKey, personIkkeFunnet().toJsonStr(PersonDato.serializer()))
            redisStore.set(arbeidsgiverFulltnavnKey, personIkkeFunnet().toJsonStr(PersonDato.serializer()))
            return Transaction.IN_PROGRESS
        }
        return Transaction.TERMINATE
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

    override fun dispatchBehov(message: JsonMessage, transaction: Transaction) {
        val transaksjonId = message[Key.UUID.str].asText().let(UUID::fromString)
        val forespoerselId = message[Key.FORESPOERSEL_ID.str].asText().let(UUID::fromString)
        when (transaction) {
            Transaction.NEW -> {
                logger.info("InnsendingService: emitting behov Virksomhet")
                rapid.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
                    DataFelt.ORGNRUNDERENHET to message[DataFelt.ORGNRUNDERENHET.str].asText().toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson()
                )

                logger.info("InnsendingService: emitting behov ARBEIDSFORHOLD")
                rapid.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to BehovType.ARBEIDSFORHOLD.toJson(),
                    Key.IDENTITETSNUMMER to message[Key.IDENTITETSNUMMER.str].asText().toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson()
                )

                logger.info("InnsendingService: emitting behov FULLT_NAVN")
                rapid.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to BehovType.FULLT_NAVN.toJson(),
                    Key.IDENTITETSNUMMER to message[Key.IDENTITETSNUMMER.str].asText().toJson(),
                    Key.ARBEIDSGIVER_ID to message[Key.ARBEIDSGIVER_ID.str].asText().toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson()
                )
            }

            Transaction.IN_PROGRESS -> {
                if (isDataCollected(*step1data(transaksjonId))) {
                    val arbeidstakerRedis = redisStore.get(RedisKey.of(transaksjonId, DataFelt.ARBEIDSTAKER_INFORMASJON))?.fromJson(PersonDato.serializer())
                    val arbeidsgiverRedis = redisStore.get(RedisKey.of(transaksjonId, DataFelt.ARBEIDSGIVER_INFORMASJON))?.fromJson(PersonDato.serializer())
                    logger.info("InnsendingService: emitting behov PERSISTER_IM")
                    rapid.publish(
                        Key.EVENT_NAME to event.toJson(),
                        Key.BEHOV to BehovType.PERSISTER_IM.toJson(),
                        DataFelt.VIRKSOMHET to redisStore.get(RedisKey.of(transaksjonId, DataFelt.VIRKSOMHET)).orDefault("Ukjent virksomhet").toJson(),
                        DataFelt.ARBEIDSTAKER_INFORMASJON to (
                            arbeidstakerRedis ?: personIkkeFunnet(message[Key.IDENTITETSNUMMER.str].asText())
                            ).toJson(PersonDato.serializer()),
                        DataFelt.ARBEIDSGIVER_INFORMASJON to (
                            arbeidsgiverRedis ?: personIkkeFunnet(message[Key.ARBEIDSGIVER_ID.str].asText())
                            ).toJson(PersonDato.serializer()),
                        DataFelt.INNTEKTSMELDING to redisStore.get(RedisKey.of(transaksjonId, DataFelt.INNTEKTSMELDING))!!.parseJson(),
                        Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                        Key.UUID to transaksjonId.toJson()
                    )
                }
            }

            else -> {
                logger.error("Illegal transaction type ecountered in dispatchBehov $transaction for uuid=$transaksjonId")
            }
        }
    }

    override fun finalize(message: JsonMessage) {
        val uuid = message[Key.UUID.str].asText().let(UUID::fromString)
        val forespoerselId = message[Key.FORESPOERSEL_ID.str].asText().let(UUID::fromString)
        val clientId = redisStore.get(RedisKey.of(uuid, event))?.let(UUID::fromString)
        logger.info("publiserer under clientID $clientId")
        redisStore.set(RedisKey.of(clientId!!), redisStore.get(RedisKey.of(uuid, DataFelt.INNTEKTSMELDING_DOKUMENT))!!)
        val erDuplikat = message[DataFelt.ER_DUPLIKAT_IM.str].asBoolean()
        if (!erDuplikat) {
            logger.info("Publiserer INNTEKTSMELDING_DOKUMENT under uuid $uuid")
            logger.info("InnsendingService: emitting event INNTEKTSMELDING_MOTTATT")
            rapid.publish(
                Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
                Key.UUID to uuid.toJson(),
                DataFelt.FORESPOERSEL_ID to forespoerselId.toJson(),
                DataFelt.INNTEKTSMELDING_DOKUMENT to message[DataFelt.INNTEKTSMELDING_DOKUMENT.str].toJsonElement()
            )
                .also {
                    logger.info("Submitting INNTEKTSMELDING_MOTTATT")
                    sikkerLogger.info("Submitting INNTEKTSMELDING_MOTTATT ${it.toPretty()}")
                }
        }
    }

    private fun step1data(uuid: UUID): Array<RedisKey> = arrayOf(
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
