package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.FailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.LagreDataRedisRiver
import no.nav.helsearbeidsgiver.felles.rapidsrivers.LagreStartDataRedisRiver
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import java.util.UUID

class InnsendingService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore
) : CompositeEventListener() {

    private val logger = logger()

    override val event = EventName.INSENDING_STARTED
    override val startKeys = setOf(
        Key.FORESPOERSEL_ID,
        Key.ORGNRUNDERENHET,
        Key.INNTEKTSMELDING,
        Key.ARBEIDSGIVER_ID,
        Key.IDENTITETSNUMMER
    )
    override val dataKeys = setOf(
        Key.VIRKSOMHET,
        Key.ARBEIDSFORHOLD,
        Key.ARBEIDSGIVER_INFORMASJON,
        Key.ARBEIDSTAKER_INFORMASJON,
        Key.INNTEKTSMELDING_DOKUMENT,
        Key.ER_DUPLIKAT_IM,
        Key.FORESPOERSEL_SVAR
    )

    private val step1Keys =
        setOf(
            Key.VIRKSOMHET,
            Key.ARBEIDSFORHOLD,
            Key.ARBEIDSTAKER_INFORMASJON,
            Key.ARBEIDSGIVER_INFORMASJON,
            Key.FORESPOERSEL_SVAR
        )

    init {
        LagreStartDataRedisRiver(event, startKeys, rapid, redisStore, ::onPacket)
        LagreDataRedisRiver(event, dataKeys, rapid, redisStore, ::onPacket)
        FailKanal(event, rapid, ::onPacket)
    }

    override fun new(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)
        val orgnr = Key.ORGNRUNDERENHET.les(String.serializer(), melding)
        val sykmeldtFnr = Key.IDENTITETSNUMMER.les(String.serializer(), melding)
        val innsenderFnr = Key.ARBEIDSGIVER_ID.les(String.serializer(), melding)

        logger.info("InnsendingService: emitting behov HENT_TRENGER_IM")
        rapid.publish(
            Key.EVENT_NAME to event.toJson(),
            Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            Key.UUID to transaksjonId.toJson()
        )

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
        val inntektsmeldingJson = Key.INNTEKTSMELDING.les(JsonElement.serializer(), melding)

        if (step1Keys.all(melding::containsKey)) {
            val virksomhetNavn = Key.VIRKSOMHET.les(String.serializer(), melding)
            val arbeidstaker = Key.ARBEIDSTAKER_INFORMASJON.les(PersonDato.serializer(), melding)
            val arbeidsgiver = Key.ARBEIDSGIVER_INFORMASJON.les(PersonDato.serializer(), melding)
            val forespoersel = Key.FORESPOERSEL_SVAR.les(TrengerInntekt.serializer(), melding)

            logger.info("InnsendingService: emitting behov PERSISTER_IM")

            rapid.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.BEHOV to BehovType.PERSISTER_IM.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.INNTEKTSMELDING to inntektsmeldingJson,
                Key.VIRKSOMHET to virksomhetNavn.toJson(),
                Key.ARBEIDSTAKER_INFORMASJON to arbeidstaker.toJson(PersonDato.serializer()),
                Key.ARBEIDSGIVER_INFORMASJON to arbeidsgiver.toJson(PersonDato.serializer()),
                Key.FORESPOERSEL_SVAR to forespoersel.toJson(TrengerInntekt.serializer())
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
        redisStore.set(RedisKey.of(clientId), inntektsmeldingJson.toString())

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

        val datafeil = when (utloesendeBehov) {
            BehovType.VIRKSOMHET -> {
                listOf(
                    Key.VIRKSOMHET to "Ukjent virksomhet".toJson()
                )
            }

            BehovType.FULLT_NAVN -> {
                val sykmeldtFnr = Key.IDENTITETSNUMMER.les(String.serializer(), melding)
                val avsenderFnr = Key.ARBEIDSGIVER_ID.les(String.serializer(), melding)

                listOf(
                    Key.ARBEIDSTAKER_INFORMASJON to tomPerson(sykmeldtFnr).toJson(PersonDato.serializer()),
                    Key.ARBEIDSGIVER_INFORMASJON to tomPerson(avsenderFnr).toJson(PersonDato.serializer())
                )
            }

            else -> {
                emptyList()
            }
        }

        if (datafeil.isNotEmpty()) {
            datafeil.onEach { (key, defaultVerdi) ->
                redisStore.set(RedisKey.of(fail.transaksjonId, key), defaultVerdi.toString())
            }

            val meldingMedDefault = datafeil.toMap().plus(melding)

            inProgress(meldingMedDefault)
        } else {
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
    }
}

private fun tomPerson(fnr: String): PersonDato =
    PersonDato(
        navn = "",
        fødselsdato = null,
        ident = fnr
    )
