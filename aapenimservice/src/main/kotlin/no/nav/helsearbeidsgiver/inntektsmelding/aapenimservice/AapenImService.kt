package no.nav.helsearbeidsgiver.inntektsmelding.aapenimservice

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Avsender
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Sykmeldt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
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
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import java.time.OffsetDateTime
import java.util.UUID

// TODO test
class AapenImService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore
) : CompositeEventListener() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override val event = EventName.AAPEN_IM_MOTTATT
    override val startKeys = listOf(
        Key.AAPEN_ID,
        Key.SKJEMA_INNTEKTSMELDING,
        Key.ARBEIDSGIVER_FNR
    )
    override val dataKeys = listOf(
        Key.VIRKSOMHET,
        Key.ARBEIDSTAKER_INFORMASJON,
        Key.ARBEIDSGIVER_INFORMASJON,
        Key.AAPEN_INNTEKTMELDING,
        Key.ER_DUPLIKAT_IM
    )

    init {
        StatefullEventListener(rapid, event, redisStore, startKeys, ::onPacket)
        StatefullDataKanal(rapid, event, redisStore, dataKeys, ::onPacket)
        FailKanal(rapid, event, ::onPacket)
    }

    override fun new(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val aapenId = Key.AAPEN_ID.les(UuidSerializer, melding)
        val skjema = Key.SKJEMA_INNTEKTSMELDING.les(SkjemaInntektsmelding.serializer(), melding)
        val avsenderFnr = Key.ARBEIDSGIVER_FNR.les(String.serializer(), melding)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(event),
            Log.transaksjonId(transaksjonId),
            Log.aapenId(aapenId)
        ) {
            rapid.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.AAPEN_ID to aapenId.toJson(),
                Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
                Key.ORGNRUNDERENHET to skjema.avsender.orgnr.toJson()
            )

            rapid.publish(
                Key.EVENT_NAME to event.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.AAPEN_ID to aapenId.toJson(),
                Key.BEHOV to BehovType.FULLT_NAVN.toJson(),
                Key.IDENTITETSNUMMER to skjema.sykmeldtFnr.toJson(),
                Key.ARBEIDSGIVER_ID to avsenderFnr.toJson()
            )
        }
    }

    override fun inProgress(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val aapenId = Key.AAPEN_ID.les(UuidSerializer, melding)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(event),
            Log.transaksjonId(transaksjonId),
            Log.aapenId(aapenId)
        ) {
            if (isDataCollected(step1data(transaksjonId))) {
                val skjema = redisStore.get(RedisKey.of(transaksjonId, Key.SKJEMA_INNTEKTSMELDING))?.fromJson(SkjemaInntektsmelding.serializer())
                val orgNavn = redisStore.get(RedisKey.of(transaksjonId, Key.VIRKSOMHET)).orDefault(ukjentVirksomhet())

                val sykmeldt = redisStore.get(RedisKey.of(transaksjonId, Key.ARBEIDSTAKER_INFORMASJON))
                    ?.fromJson(PersonDato.serializer())
                    .orDefault {
                        "Fant ikke arbeidstakerinformasjon i Redis.".also {
                            logger.error(it)
                            sikkerLogger.error(it)
                        }
                        val sykmeldtFnr = Key.IDENTITETSNUMMER.les(String.serializer(), melding)
                        tomPerson(sykmeldtFnr)
                    }

                val avsender = redisStore.get(RedisKey.of(transaksjonId, Key.ARBEIDSGIVER_INFORMASJON))
                    ?.fromJson(PersonDato.serializer())
                    .orDefault {
                        "Fant ikke arbeidsgiverinformasjon i Redis.".also {
                            logger.error(it)
                            sikkerLogger.error(it)
                        }
                        val avsenderFnr = Key.ARBEIDSGIVER_ID.les(String.serializer(), melding)
                        tomPerson(avsenderFnr)
                    }

                if (skjema != null) {
                    val inntektsmelding = tilInntektsmelding(
                        aapenId = aapenId,
                        skjema = skjema,
                        orgNavn = orgNavn,
                        sykmeldt = sykmeldt,
                        avsender = avsender
                    )

                    logger.debug("Skal sende melding med behov 'BehovType.LAGRE_AAPEN_IM'")
                    sikkerLogger.debug("Skal sende melding med behov 'BehovType.LAGRE_AAPEN_IM'")
//                    rapid.publish(
//                        Key.EVENT_NAME to event.toJson(),
//                        Key.UUID to transaksjonId.toJson(),
//                        Key.AAPEN_ID to aapenId.toJson(),
//                        Key.BEHOV to BehovType.LAGRE_AAPEN_IM.toJson(),
//                        Key.AAPEN_INNTEKTMELDING to inntektsmelding.toJson(Inntektsmelding.serializer())
//                    )

                    // TODO Midlertidig sett svar til im-api
                    val clientId = redisStore.get(RedisKey.of(transaksjonId, event))!!.let(UUID::fromString)
                    redisStore.set(RedisKey.of(clientId), inntektsmelding.toJsonStr(Inntektsmelding.serializer()))
                } else {
                    "Fant ikke skjema i Redis. Kan ikke fortsette.".also {
                        logger.error(it)
                        sikkerLogger.error(it)
                    }
                }
            }
        }
    }

    override fun finalize(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val aapenId = Key.AAPEN_ID.les(UuidSerializer, melding)
        val erDuplikat = Key.ER_DUPLIKAT_IM.les(Boolean.serializer(), melding)
        val inntektsmeldingJson = Key.AAPEN_INNTEKTMELDING.les(JsonElement.serializer(), melding)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(event),
            Log.transaksjonId(transaksjonId),
            Log.aapenId(aapenId)
        ) {
            val clientId = redisStore.get(RedisKey.of(transaksjonId, event))?.let(UUID::fromString)

            if (clientId == null) {
                sikkerLogger.error("Forsøkte å fullføre, men clientId mangler i Redis.")
            } else {
                redisStore.set(RedisKey.of(clientId), inntektsmeldingJson.toString())
            }

            if (!erDuplikat) {
                rapid.publish(
                    Key.EVENT_NAME to EventName.AAPEN_IM_LAGRET.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.AAPEN_ID to aapenId.toJson(),
                    Key.AAPEN_INNTEKTMELDING to inntektsmeldingJson
                )
                    .also {
                        MdcUtils.withLogFields(
                            Log.event(EventName.AAPEN_IM_LAGRET)
                        ) {
                            logger.info("Publiserte melding.")
                            sikkerLogger.info("Publiserte melding:\n${it.toPretty()}")
                        }
                    }
            }
        }
    }

    override fun onError(melding: Map<Key, JsonElement>, fail: Fail) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(EventName.AAPEN_IM_MOTTATT),
            Log.transaksjonId(fail.transaksjonId)
        ) {
            val utloesendeBehov = Key.BEHOV.lesOrNull(BehovType.serializer(), fail.utloesendeMelding.toMap())
            if (utloesendeBehov == BehovType.VIRKSOMHET) {
                val virksomhetKey = RedisKey.of(fail.transaksjonId, Key.VIRKSOMHET)
                redisStore.set(virksomhetKey, ukjentVirksomhet())
                return inProgress(melding)
            } else if (utloesendeBehov == BehovType.FULLT_NAVN) {
                val arbeidstakerKey = RedisKey.of(fail.transaksjonId, Key.ARBEIDSTAKER_INFORMASJON)
                val arbeidsgiverKey = RedisKey.of(fail.transaksjonId, Key.ARBEIDSGIVER_INFORMASJON)
                redisStore.set(arbeidstakerKey, tomPerson().toJsonStr(PersonDato.serializer()))
                redisStore.set(arbeidsgiverKey, tomPerson().toJsonStr(PersonDato.serializer()))
                return inProgress(melding)
            }

            val clientId = redisStore.get(RedisKey.of(fail.transaksjonId, event))
                ?.let(UUID::fromString)

            if (clientId == null) {
                val aapenId = Key.AAPEN_ID.lesOrNull(UuidSerializer, fail.utloesendeMelding.toMap())
                sikkerLogger.error("Forsøkte å terminere, men clientId mangler i Redis. aapenId=$aapenId")
            } else {
                redisStore.set(RedisKey.of(clientId), fail.feilmelding)
            }
        }
    }
}

private fun tilInntektsmelding(
    aapenId: UUID,
    skjema: SkjemaInntektsmelding,
    orgNavn: String,
    sykmeldt: PersonDato,
    avsender: PersonDato
): Inntektsmelding =
    Inntektsmelding(
        id = aapenId,
        sykmeldt = Sykmeldt(
            fnr = sykmeldt.ident,
            navn = sykmeldt.navn
        ),
        avsender = Avsender(
            orgnr = skjema.avsender.orgnr,
            orgNavn = orgNavn,
            fnr = avsender.ident,
            navn = avsender.navn,
            tlf = skjema.avsender.tlf
        ),
        sykmeldingsperioder = skjema.sykmeldingsperioder,
        agp = skjema.agp,
        inntekt = skjema.inntekt,
        refusjon = skjema.refusjon,
        aarsakInnsending = skjema.aarsakInnsending,
        mottatt = OffsetDateTime.now()
    )

private fun step1data(transaksjonId: UUID): List<RedisKey> =
    listOf(
        Key.VIRKSOMHET,
        Key.ARBEIDSTAKER_INFORMASJON,
        Key.ARBEIDSGIVER_INFORMASJON
    )
        .map { RedisKey.of(transaksjonId, it) }

private fun ukjentVirksomhet(): String =
    "Ukjent virksomhet"

private fun tomPerson(ident: String = "") = PersonDato(
    navn = "",
    fødselsdato = null,
    ident = ident
)
