package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
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
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
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

    override fun new(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)
        val orgnr = Key.ORGNRUNDERENHET.les(String.serializer(), melding)
        val sykmeldtFnr = Key.IDENTITETSNUMMER.les(String.serializer(), melding)
        val innsenderFnr = Key.ARBEIDSGIVER_ID.les(String.serializer(), melding)

        logger.info("InnsendingService: emitting behov Virksomhet")
        rapid.publish(
            Key.EVENT_NAME to event.toJson(),
            Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
            Key.ORGNRUNDERENHET to orgnr.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson()
        )

        logger.info("InnsendingService: emitting behov ARBEIDSFORHOLD")
        rapid.publish(
            Key.EVENT_NAME to event.toJson(),
            Key.BEHOV to BehovType.ARBEIDSFORHOLD.toJson(),
            Key.IDENTITETSNUMMER to sykmeldtFnr.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson()
        )

        logger.info("InnsendingService: emitting behov FULLT_NAVN")
        rapid.publish(
            Key.EVENT_NAME to event.toJson(),
            Key.BEHOV to BehovType.FULLT_NAVN.toJson(),
            Key.IDENTITETSNUMMER to sykmeldtFnr.toJson(),
            Key.ARBEIDSGIVER_ID to innsenderFnr.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson()
        )
    }

    override fun inProgress(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)

        if (isDataCollected(step1data(transaksjonId))) {
            val arbeidstaker = redisStore.get(RedisKey.of(transaksjonId, Key.ARBEIDSTAKER_INFORMASJON))
                ?.fromJson(PersonDato.serializer())
                .orDefault {
                    "Fant ikke arbeidstakerinformasjon i Redis.".also {
                        logger.error(it)
                        sikkerLogger.error(it)
                    }
                    val sykmeldtFnr = Key.IDENTITETSNUMMER.les(String.serializer(), melding)
                    personIkkeFunnet(sykmeldtFnr)
                }

            val arbeidsgiver = redisStore.get(RedisKey.of(transaksjonId, Key.ARBEIDSGIVER_INFORMASJON))
                ?.fromJson(PersonDato.serializer())
                .orDefault {
                    "Fant ikke arbeidsgiverinformasjon i Redis.".also {
                        logger.error(it)
                        sikkerLogger.error(it)
                    }
                    val innsenderFnr = Key.ARBEIDSGIVER_ID.les(String.serializer(), melding)
                    personIkkeFunnet(innsenderFnr)
                }

            val virksomhetNavn = redisStore.get(RedisKey.of(transaksjonId, Key.VIRKSOMHET)).orDefault("Ukjent virksomhet")
            val inntektsmeldingJson = redisStore.get(RedisKey.of(transaksjonId, Key.INNTEKTSMELDING))!!.parseJson()

            logger.info("InnsendingService: emitting behov PERSISTER_IM")

            rapid.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.PERSISTER_IM.toJson(),
                Key.VIRKSOMHET to virksomhetNavn.toJson(),
                Key.ARBEIDSTAKER_INFORMASJON to arbeidstaker.toJson(PersonDato.serializer()),
                Key.ARBEIDSGIVER_INFORMASJON to arbeidsgiver.toJson(PersonDato.serializer()),
                Key.INNTEKTSMELDING to inntektsmeldingJson,
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.UUID to transaksjonId.toJson()
            )
        }
    }

    override fun finalize(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)
        val erDuplikat = Key.ER_DUPLIKAT_IM.les(Boolean.serializer(), melding)
        val inntektsmeldingJson = Key.INNTEKTSMELDING_DOKUMENT.les(JsonElement.serializer(), melding)

        val clientId = redisStore.get(RedisKey.of(transaksjonId, event))!!.let(UUID::fromString)

        logger.info("publiserer under clientID $clientId")
        redisStore.set(RedisKey.of(clientId), redisStore.get(RedisKey.of(transaksjonId, Key.INNTEKTSMELDING_DOKUMENT))!!)

        if (!erDuplikat) {
            logger.info("Publiserer INNTEKTSMELDING_DOKUMENT under uuid $transaksjonId")
            logger.info("InnsendingService: emitting event INNTEKTSMELDING_MOTTATT")
            rapid.publish(
                Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.INNTEKTSMELDING_DOKUMENT to inntektsmeldingJson
            )
                .also {
                    logger.info("Submitting INNTEKTSMELDING_MOTTATT")
                    sikkerLogger.info("Submitting INNTEKTSMELDING_MOTTATT ${it.toPretty()}")
                }
        }
    }

    override fun onError(melding: Map<Key, JsonElement>, fail: Fail) {
        val utloesendeBehov = Key.BEHOV.lesOrNull(BehovType.serializer(), fail.utloesendeMelding.toMap())

        if (utloesendeBehov == BehovType.VIRKSOMHET) {
            val virksomhetKey = RedisKey.of(fail.transaksjonId, Key.VIRKSOMHET)
            redisStore.set(virksomhetKey, "Ukjent virksomhet")
            return inProgress(melding)
        } else if (utloesendeBehov == BehovType.FULLT_NAVN) {
            val arbeidstakerFulltnavnKey = RedisKey.of(fail.transaksjonId, Key.ARBEIDSTAKER_INFORMASJON)
            val arbeidsgiverFulltnavnKey = RedisKey.of(fail.transaksjonId, Key.ARBEIDSGIVER_INFORMASJON)
            redisStore.set(arbeidstakerFulltnavnKey, personIkkeFunnet().toJsonStr(PersonDato.serializer()))
            redisStore.set(arbeidsgiverFulltnavnKey, personIkkeFunnet().toJsonStr(PersonDato.serializer()))
            return inProgress(melding)
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

    private fun step1data(uuid: UUID): Set<RedisKey> =
        setOf(
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
