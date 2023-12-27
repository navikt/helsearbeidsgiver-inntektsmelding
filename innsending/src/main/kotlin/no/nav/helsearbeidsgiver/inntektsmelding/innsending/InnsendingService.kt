package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.DelegatingFailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
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
                    Key.VIRKSOMHET,
                    Key.ARBEIDSFORHOLD,
                    Key.INNTEKTSMELDING_DOKUMENT,
                    Key.ARBEIDSGIVER_INFORMASJON,
                    Key.ARBEIDSTAKER_INFORMASJON,
                    Key.ER_DUPLIKAT_IM
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
                    Key.FORESPOERSEL_ID,
                    Key.ORGNRUNDERENHET,
                    Key.INNTEKTSMELDING,
                    Key.ARBEIDSGIVER_ID,
                    Key.IDENTITETSNUMMER
                ),
                it,
                rapid
            )
        }
    }

    override fun onError(feil: Fail): Transaction {
        val utloesendeBehov = Key.BEHOV.lesOrNull(BehovType.serializer(), feil.utloesendeMelding.toMap())

        if (utloesendeBehov == BehovType.VIRKSOMHET) {
            val virksomhetKey = RedisKey.of(feil.transaksjonId, Key.VIRKSOMHET)
            redisStore.set(virksomhetKey, "Ukjent virksomhet")
            return Transaction.IN_PROGRESS
        } else if (utloesendeBehov == BehovType.FULLT_NAVN) {
            val arbeidstakerFulltnavnKey = RedisKey.of(feil.transaksjonId, Key.ARBEIDSTAKER_INFORMASJON)
            val arbeidsgiverFulltnavnKey = RedisKey.of(feil.transaksjonId, Key.ARBEIDSGIVER_INFORMASJON)
            redisStore.set(arbeidstakerFulltnavnKey, personIkkeFunnet().toJsonStr(PersonDato.serializer()))
            redisStore.set(arbeidsgiverFulltnavnKey, personIkkeFunnet().toJsonStr(PersonDato.serializer()))
            return Transaction.IN_PROGRESS
        }
        return Transaction.TERMINATE
    }

    override fun terminate(fail: Fail) {
        val clientId = redisStore.get(RedisKey.of(fail.transaksjonId, event))
            ?.let(UUID::fromString)

        if (clientId == null) {
            MdcUtils.withLogFields(
                Log.transaksjonId(fail.transaksjonId)
            ) {
                sikkerLogger.error("Forsøkte å terminere, men clientId mangler i Redis. forespoerselId=${fail.forespoerselId}")
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
                    Key.ORGNRUNDERENHET to message[Key.ORGNRUNDERENHET.str].asText().toJson(),
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
                    val arbeidstakerRedis = redisStore.get(RedisKey.of(transaksjonId, Key.ARBEIDSTAKER_INFORMASJON))?.fromJson(PersonDato.serializer())
                    val arbeidsgiverRedis = redisStore.get(RedisKey.of(transaksjonId, Key.ARBEIDSGIVER_INFORMASJON))?.fromJson(PersonDato.serializer())
                    logger.info("InnsendingService: emitting behov PERSISTER_IM")
                    rapid.publish(
                        Key.EVENT_NAME to event.toJson(),
                        Key.BEHOV to BehovType.PERSISTER_IM.toJson(),
                        Key.VIRKSOMHET to redisStore.get(RedisKey.of(transaksjonId, Key.VIRKSOMHET)).orDefault("Ukjent virksomhet").toJson(),
                        Key.ARBEIDSTAKER_INFORMASJON to (
                            arbeidstakerRedis ?: personIkkeFunnet(message[Key.IDENTITETSNUMMER.str].asText())
                            ).toJson(PersonDato.serializer()),
                        Key.ARBEIDSGIVER_INFORMASJON to (
                            arbeidsgiverRedis ?: personIkkeFunnet(message[Key.ARBEIDSGIVER_ID.str].asText())
                            ).toJson(PersonDato.serializer()),
                        Key.INNTEKTSMELDING to redisStore.get(RedisKey.of(transaksjonId, Key.INNTEKTSMELDING))!!.parseJson(),
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
        redisStore.set(RedisKey.of(clientId!!), redisStore.get(RedisKey.of(uuid, Key.INNTEKTSMELDING_DOKUMENT))!!)
        val erDuplikat = message[Key.ER_DUPLIKAT_IM.str].asBoolean()
        if (!erDuplikat) {
            logger.info("Publiserer INNTEKTSMELDING_DOKUMENT under uuid $uuid")
            logger.info("InnsendingService: emitting event INNTEKTSMELDING_MOTTATT")
            rapid.publish(
                Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
                Key.UUID to uuid.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.INNTEKTSMELDING_DOKUMENT to message[Key.INNTEKTSMELDING_DOKUMENT.str].toString().parseJson()
            )
                .also {
                    logger.info("Submitting INNTEKTSMELDING_MOTTATT")
                    sikkerLogger.info("Submitting INNTEKTSMELDING_MOTTATT ${it.toPretty()}")
                }
        }
    }

    private fun step1data(uuid: UUID): Array<RedisKey> = arrayOf(
        RedisKey.of(uuid, Key.VIRKSOMHET),
        RedisKey.of(uuid, Key.ARBEIDSFORHOLD),
        RedisKey.of(uuid, Key.ARBEIDSTAKER_INFORMASJON),
        RedisKey.of(uuid, Key.ARBEIDSGIVER_INFORMASJON)
    )

    private fun personIkkeFunnet(ident: String = "") = PersonDato(
        navn = "",
        fødselsdato = null,
        ident = ident
    )
}
