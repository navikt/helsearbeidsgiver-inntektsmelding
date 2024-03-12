package no.nav.helsearbeidsgiver.inntektsmelding.aapenimservice

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Avsender
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Sykmeldt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Person
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.FailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.LagreDataRedisRiver
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
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
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.time.OffsetDateTime
import java.util.UUID

// TODO test
class LagreAapenImService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore
) : CompositeEventListener() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override val event = EventName.AAPEN_IM_MOTTATT
    override val startKeys = setOf(
        Key.AAPEN_ID,
        Key.SKJEMA_INNTEKTSMELDING,
        Key.ARBEIDSGIVER_FNR
    )
    override val dataKeys = setOf(
        Key.VIRKSOMHET,
        Key.PERSONER,
        Key.AAPEN_INNTEKTMELDING,
        Key.ER_DUPLIKAT_IM,
        Key.SAK_ID
    )

    private val step1Keys = setOf(
        Key.VIRKSOMHET,
        Key.PERSONER
    )
    private val step2Keys = setOf(
        Key.AAPEN_INNTEKTMELDING,
        Key.ER_DUPLIKAT_IM
    )

    init {
        StatefullEventListener(event, startKeys, rapid, redisStore, ::onPacket)
        LagreDataRedisRiver(event, dataKeys, rapid, redisStore, ::onPacket)
        FailKanal(event, rapid, ::onPacket)
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
                Key.BEHOV to BehovType.HENT_PERSONER.toJson(),
                Key.FNR_LISTE to listOf(
                    skjema.sykmeldtFnr,
                    avsenderFnr
                ).toJson(String.serializer())
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
            if (step2Keys.all(melding::containsKey)) {
                val inntektsmelding = Key.AAPEN_INNTEKTMELDING.les(Inntektsmelding.serializer(), melding)

                when (inntektsmelding.aarsakInnsending) {
                    AarsakInnsending.Endring -> {
                        finalize(melding)
                    }
                    AarsakInnsending.Ny -> {
                        rapid.publish(
                            Key.EVENT_NAME to event.toJson(),
                            Key.BEHOV to BehovType.OPPRETT_AAPEN_SAK.toJson(),
                            Key.UUID to transaksjonId.toJson(),
                            Key.AAPEN_INNTEKTMELDING to inntektsmelding.toJson(Inntektsmelding.serializer())
                        )
                            .also {
                                logger.info("Publiserte melding med behov '${BehovType.OPPRETT_AAPEN_SAK}'.")
                                sikkerLogger.info("Publiserte melding:\n${it.toPretty()}")
                            }
                    }
                }
            } else if (step1Keys.all(melding::containsKey)) {
                val skjema = Key.SKJEMA_INNTEKTSMELDING.les(SkjemaInntektsmelding.serializer(), melding)
                val avsenderFnr = Key.ARBEIDSGIVER_FNR.les(String.serializer(), melding)
                val orgNavn = Key.VIRKSOMHET.les(String.serializer(), melding)
                val personer = Key.PERSONER.les(personerMapSerializer, melding)

                val sykmeldt = skjema.sykmeldtFnr.let {
                    personer[it] ?: tomPerson(it)
                }
                val avsender = avsenderFnr.let {
                    personer[it] ?: tomPerson(it)
                }

                val inntektsmelding = tilInntektsmelding(
                    aapenId = aapenId,
                    skjema = skjema,
                    orgNavn = orgNavn,
                    sykmeldt = sykmeldt,
                    avsender = avsender
                )

                rapid.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.BEHOV to BehovType.LAGRE_AAPEN_IM.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.AAPEN_INNTEKTMELDING to inntektsmelding.toJson(Inntektsmelding.serializer())
                )
                    .also {
                        logger.info("Publiserte melding med behov '${BehovType.LAGRE_AAPEN_IM}'.")
                        sikkerLogger.info("Publiserte melding:\n${it.toPretty()}")
                    }
            } else {
                Unit
            }
        }
    }

    override fun finalize(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val inntektsmelding = Key.AAPEN_INNTEKTMELDING.les(Inntektsmelding.serializer(), melding)
        val erDuplikat = Key.ER_DUPLIKAT_IM.les(Boolean.serializer(), melding)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(event),
            Log.transaksjonId(transaksjonId),
            Log.aapenId(inntektsmelding.id)
        ) {
            val clientId = redisStore.get(RedisKey.of(transaksjonId, event))?.let(UUID::fromString)
            val inntektsmeldingJson = inntektsmelding.toJson(Inntektsmelding.serializer())

            if (clientId == null) {
                sikkerLogger.error("Forsøkte å fullføre, men clientId mangler i Redis.")
            } else {
                redisStore.set(RedisKey.of(clientId), inntektsmeldingJson.toString())
            }

            if (!erDuplikat) {
                rapid.publish(
                    Key.EVENT_NAME to EventName.AAPEN_IM_LAGRET.toJson(),
                    Key.UUID to transaksjonId.toJson(),
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
            Log.event(event),
            Log.transaksjonId(fail.transaksjonId)
        ) {
            val utloesendeBehov = Key.BEHOV.lesOrNull(BehovType.serializer(), fail.utloesendeMelding.toMap())
            val datafeil =
                when (utloesendeBehov) {
                    BehovType.VIRKSOMHET -> Key.VIRKSOMHET to "Ukjent virksomhet".toJson()

                    // Lesing av personer bruker allerede defaults, så trenger bare map-struktur her
                    BehovType.FULLT_NAVN -> Key.PERSONER to emptyMap<String, JsonElement>().toJson()

                    else -> null
                }

            if (datafeil != null) {
                redisStore.set(RedisKey.of(fail.transaksjonId, datafeil.first), datafeil.second.toString())

                val meldingMedDefault = mapOf(datafeil) + melding

                return inProgress(meldingMedDefault)
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

private val personerMapSerializer =
    MapSerializer(
        String.serializer(),
        Person.serializer()
    )

private fun tilInntektsmelding(
    aapenId: UUID,
    type: Inntektsmelding.Type = Inntektsmelding.Type.SELVBESTEMT,
    skjema: SkjemaInntektsmelding,
    orgNavn: String,
    sykmeldt: Person,
    avsender: Person
): Inntektsmelding =
    Inntektsmelding(
        id = aapenId,
        sykmeldt = Sykmeldt(
            fnr = sykmeldt.fnr,
            navn = sykmeldt.navn
        ),
        avsender = Avsender(
            orgnr = skjema.avsender.orgnr,
            orgNavn = orgNavn,
            fnr = avsender.fnr,
            navn = avsender.navn,
            tlf = skjema.avsender.tlf
        ),
        sykmeldingsperioder = skjema.sykmeldingsperioder,
        agp = skjema.agp,
        inntekt = skjema.inntekt,
        refusjon = skjema.refusjon,
        aarsakInnsending = skjema.aarsakInnsending,
        mottatt = OffsetDateTime.now(),
        type = type
    )

private fun tomPerson(fnr: String): Person =
    Person(
        fnr = fnr,
        navn = "",
        foedselsdato = Person.foedselsdato(fnr)
    )
