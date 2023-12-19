package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.FailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
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
) : CompositeEventListener() {

    private val logger = logger()

    override val event = EventName.INSENDING_STARTED
    override val startKeys = listOf(
        Key.FORESPOERSEL_ID,
        Key.ORGNRUNDERENHET,
        Key.INNTEKTSMELDING,
        Key.ARBEIDSGIVER_ID,
        Key.IDENTITETSNUMMER
    )
    override val dataKeys = listOf(
        Key.VIRKSOMHET,
        Key.ARBEIDSFORHOLD,
        Key.INNTEKTSMELDING_DOKUMENT,
        Key.ARBEIDSGIVER_INFORMASJON,
        Key.ARBEIDSTAKER_INFORMASJON,
        Key.ER_DUPLIKAT_IM
    )

    init {
        StatefullEventListener(rapid, event, redisStore, startKeys, ::onPacket)
        StatefullDataKanal(rapid, event, redisStore, dataKeys, ::onPacket)
        FailKanal(rapid, event, ::onPacket)
    }

    override fun new(message: JsonMessage) {
        val transaksjonId = message[Key.UUID.str].asText().let(UUID::fromString)
        val forespoerselId = message[Key.FORESPOERSEL_ID.str].asText().let(UUID::fromString)

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

    override fun inProgress(message: JsonMessage) {
        val transaksjonId = message[Key.UUID.str].asText().let(UUID::fromString)
        val forespoerselId = message[Key.FORESPOERSEL_ID.str].asText().let(UUID::fromString)

        if (isDataCollected(step1data(transaksjonId))) {
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
                Key.INNTEKTSMELDING_DOKUMENT to message[Key.INNTEKTSMELDING_DOKUMENT.str].toJsonElement()
            )
                .also {
                    logger.info("Submitting INNTEKTSMELDING_MOTTATT")
                    sikkerLogger.info("Submitting INNTEKTSMELDING_MOTTATT ${it.toPretty()}")
                }
        }
    }

    override fun onError(message: JsonMessage, fail: Fail) {
        val utloesendeBehov = Key.BEHOV.lesOrNull(BehovType.serializer(), fail.utloesendeMelding.toMap())

        if (utloesendeBehov == BehovType.VIRKSOMHET) {
            val virksomhetKey = RedisKey.of(fail.transaksjonId, Key.VIRKSOMHET)
            redisStore.set(virksomhetKey, "Ukjent virksomhet")
            return inProgress(message)
        } else if (utloesendeBehov == BehovType.FULLT_NAVN) {
            val arbeidstakerFulltnavnKey = RedisKey.of(fail.transaksjonId, Key.ARBEIDSTAKER_INFORMASJON)
            val arbeidsgiverFulltnavnKey = RedisKey.of(fail.transaksjonId, Key.ARBEIDSGIVER_INFORMASJON)
            redisStore.set(arbeidstakerFulltnavnKey, personIkkeFunnet().toJsonStr(PersonDato.serializer()))
            redisStore.set(arbeidsgiverFulltnavnKey, personIkkeFunnet().toJsonStr(PersonDato.serializer()))
            return inProgress(message)
        }

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

    private fun step1data(uuid: UUID): List<RedisKey> = listOf(
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
