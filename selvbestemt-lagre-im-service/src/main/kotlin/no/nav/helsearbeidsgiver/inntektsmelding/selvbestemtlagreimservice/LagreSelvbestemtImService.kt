package no.nav.helsearbeidsgiver.inntektsmelding.selvbestemtlagreimservice

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Avsender
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Sykmeldt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmeldingSelvbestemt
import no.nav.helsearbeidsgiver.felles.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Person
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publishNotNull
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.Service
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceMed3Steg
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.felles.utils.aktivtArbeidsforholdIPeriode
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class Steg0(
    val transaksjonId: UUID,
    val skjema: SkjemaInntektsmeldingSelvbestemt,
    val avsenderFnr: Fnr,
)

sealed class Steg1 {
    data class Komplett(
        val orgNavn: String,
        val personer: Map<Fnr, Person>,
        val arbeidsforhold: List<Arbeidsforhold>,
    ) : Steg1()

    data object Delvis : Steg1()
}

data class Steg2(
    val inntektsmelding: Inntektsmelding,
    val erDuplikat: Boolean,
)

/** @property sakId nullable fordi man ikke alltid oppretter sak (se [LagreSelvbestemtImService.utfoerSteg2]) */
data class Steg3(
    val sakId: String?,
)

class LagreSelvbestemtImService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore,
) : ServiceMed3Steg<Steg0, Steg1, Steg2, Steg3>(),
    Service.MedRedis {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.SELVBESTEMT_IM_MOTTATT
    override val startKeys =
        setOf(
            Key.SKJEMA_INNTEKTSMELDING,
            Key.ARBEIDSGIVER_FNR,
        )
    override val dataKeys =
        setOf(
            Key.VIRKSOMHET,
            Key.PERSONER,
            Key.ARBEIDSFORHOLD,
            Key.SELVBESTEMT_INNTEKTSMELDING,
            Key.ER_DUPLIKAT_IM,
            Key.SAK_ID,
        )

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            transaksjonId = Key.UUID.les(UuidSerializer, melding),
            skjema = Key.SKJEMA_INNTEKTSMELDING.les(SkjemaInntektsmeldingSelvbestemt.serializer(), melding),
            avsenderFnr = Key.ARBEIDSGIVER_FNR.les(Fnr.serializer(), melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 {
        val steg0 = lesSteg0(melding)

        val orgNavn = runCatching { Key.VIRKSOMHET.les(String.serializer(), melding) }
        val personer = runCatching { Key.PERSONER.les(personMapSerializer, melding) }
        val arbeidsforhold =
            runCatching {
                Key.ARBEIDSFORHOLD
                    .les(Arbeidsforhold.serializer().list(), melding)
                    .filter { it.arbeidsgiver.organisasjonsnummer == steg0.skjema.avsender.orgnr.verdi }
            }

        val results = listOf(orgNavn, personer, arbeidsforhold)

        return if (results.all { it.isSuccess }) {
            Steg1.Komplett(
                orgNavn = orgNavn.getOrThrow(),
                personer = personer.getOrThrow(),
                arbeidsforhold = arbeidsforhold.getOrThrow(),
            )
        } else if (results.any { it.isSuccess }) {
            Steg1.Delvis
        } else {
            throw results.firstNotNullOf { it.exceptionOrNull() }
        }
    }

    override fun lesSteg2(melding: Map<Key, JsonElement>): Steg2 =
        Steg2(
            inntektsmelding = Key.SELVBESTEMT_INNTEKTSMELDING.les(Inntektsmelding.serializer(), melding),
            erDuplikat = Key.ER_DUPLIKAT_IM.les(Boolean.serializer(), melding),
        )

    override fun lesSteg3(melding: Map<Key, JsonElement>): Steg3 =
        Steg3(
            sakId = Key.SAK_ID.les(String.serializer(), melding),
        )

    override fun utfoerSteg0(steg0: Steg0) {
        kontrollerSkjema(steg0.skjema)

        rapid.publishNotNull(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
            Key.UUID to steg0.transaksjonId.toJson(),
            Key.SELVBESTEMT_ID to steg0.skjema.selvbestemtId?.toJson(),
            Key.ORGNRUNDERENHET to
                steg0.skjema.avsender.orgnr
                    .toJson(),
        )

        rapid.publishNotNull(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to BehovType.HENT_PERSONER.toJson(),
            Key.UUID to steg0.transaksjonId.toJson(),
            Key.SELVBESTEMT_ID to steg0.skjema.selvbestemtId?.toJson(),
            Key.FNR_LISTE to
                listOf(
                    steg0.skjema.sykmeldtFnr,
                    steg0.avsenderFnr,
                ).toJson(Fnr.serializer()),
        )

        rapid.publishNotNull(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to BehovType.HENT_ARBEIDSFORHOLD.toJson(),
            Key.UUID to steg0.transaksjonId.toJson(),
            Key.IDENTITETSNUMMER to
                steg0.skjema.sykmeldtFnr.verdi
                    .toJson(),
            Key.SELVBESTEMT_ID to steg0.skjema.selvbestemtId?.toJson(),
        )
    }

    override fun utfoerSteg1(
        steg0: Steg0,
        steg1: Steg1,
    ) {
        if (steg1 is Steg1.Komplett) {
            val sykmeldtNavn = steg1.personer[steg0.skjema.sykmeldtFnr]?.navn.orEmpty()
            val avsenderNavn = steg1.personer[steg0.avsenderFnr]?.navn.orEmpty()

            val inntektsmelding =
                tilInntektsmelding(
                    skjema = steg0.skjema,
                    orgNavn = steg1.orgNavn,
                    sykmeldtNavn = sykmeldtNavn,
                    avsenderNavn = avsenderNavn,
                )

            val sykeperioder =
                steg0.skjema.agp
                    ?.perioder
                    .orEmpty()
                    .plus(steg0.skjema.sykmeldingsperioder)
            val erAktivtArbeidsforhold = sykeperioder.aktivtArbeidsforholdIPeriode(steg1.arbeidsforhold)

            if (erAktivtArbeidsforhold) {
                rapid
                    .publish(
                        Key.EVENT_NAME to eventName.toJson(),
                        Key.BEHOV to BehovType.LAGRE_SELVBESTEMT_IM.toJson(),
                        Key.UUID to steg0.transaksjonId.toJson(),
                        Key.SELVBESTEMT_INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
                    ).also {
                        logger.info("Publiserte melding med behov '${BehovType.LAGRE_SELVBESTEMT_IM}'.")
                        sikkerLogger.info("Publiserte melding:\n${it.toPretty()}")
                    }
            } else {
                "Mangler arbeidsforhold i perioden".also { feilmelding ->
                    logger.warn(feilmelding)
                    sikkerLogger.warn(feilmelding)
                    val resultJson = ResultJson(failure = feilmelding.toJson()).toJson(ResultJson.serializer())
                    redisStore.set(RedisKey.of(steg0.transaksjonId), resultJson)
                }
            }
        }
    }

    override fun utfoerSteg2(
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
    ) {
        when (steg2.inntektsmelding.aarsakInnsending) {
            AarsakInnsending.Endring -> {
                utfoerSteg3(steg0, steg1, steg2, Steg3(null))
            }

            AarsakInnsending.Ny -> {
                rapid
                    .publish(
                        Key.EVENT_NAME to eventName.toJson(),
                        Key.BEHOV to BehovType.OPPRETT_SELVBESTEMT_SAK.toJson(),
                        Key.UUID to steg0.transaksjonId.toJson(),
                        Key.SELVBESTEMT_INNTEKTSMELDING to steg2.inntektsmelding.toJson(Inntektsmelding.serializer()),
                    ).also {
                        logger.info("Publiserte melding med behov '${BehovType.OPPRETT_SELVBESTEMT_SAK}'.")
                        sikkerLogger.info("Publiserte melding:\n${it.toPretty()}")
                    }
            }
        }
    }

    override fun utfoerSteg3(
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
        steg3: Steg3,
    ) {
        MdcUtils.withLogFields(
            Log.selvbestemtId(steg2.inntektsmelding.type.id),
        ) {
            val resultJson =
                ResultJson(
                    success =
                        steg2.inntektsmelding.type.id
                            .toJson(),
                ).toJson(ResultJson.serializer())
            redisStore.set(RedisKey.of(steg0.transaksjonId), resultJson)

            if (!steg2.erDuplikat) {
                val publisert =
                    rapid.publish(
                        Key.EVENT_NAME to EventName.SELVBESTEMT_IM_LAGRET.toJson(),
                        Key.UUID to steg0.transaksjonId.toJson(),
                        Key.SELVBESTEMT_ID to
                            steg2.inntektsmelding.type.id
                                .toJson(),
                        Key.SELVBESTEMT_INNTEKTSMELDING to steg2.inntektsmelding.toJson(Inntektsmelding.serializer()),
                    )

                MdcUtils.withLogFields(
                    Log.event(EventName.SELVBESTEMT_IM_LAGRET),
                ) {
                    logger.info("Publiserte melding.")
                    sikkerLogger.info("Publiserte melding:\n${publisert.toPretty()}")
                }
            }
        }
    }

    override fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    ) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(eventName),
            Log.transaksjonId(fail.transaksjonId),
        ) {
            val utloesendeBehov = Key.BEHOV.lesOrNull(BehovType.serializer(), fail.utloesendeMelding.toMap())
            val datafeil =
                when (utloesendeBehov) {
                    BehovType.VIRKSOMHET -> Key.VIRKSOMHET to "Ukjent virksomhet".toJson()

                    // Lesing av personer bruker allerede defaults, så trenger bare map-struktur her
                    BehovType.HENT_PERSONER -> Key.PERSONER to emptyMap<Fnr, Person>().toJson(personMapSerializer)

                    else -> null
                }

            if (datafeil != null) {
                redisStore.set(RedisKey.of(fail.transaksjonId, datafeil.first), datafeil.second)

                val meldingMedDefault = mapOf(datafeil) + melding

                onData(meldingMedDefault)
            } else {
                val resultJson = ResultJson(failure = fail.feilmelding.toJson()).toJson(ResultJson.serializer())
                redisStore.set(RedisKey.of(fail.transaksjonId), resultJson)
            }
        }
    }

    private fun kontrollerSkjema(skjema: SkjemaInntektsmeldingSelvbestemt) {
        skjema.agp?.let { arbeidsgiverperiode ->
            if (arbeidsgiverperiode.perioder.sumOf
                    {
                        it.fom.datesUntil(it.tom).count() + 1 // datesuntil er eksklusiv t.o.m, så legg til 1
                    } < 16
            ) {
                sikkerLogger.info("Skjema fra orgnr ${skjema.avsender.orgnr} har kort AGP")
            }
        }
        skjema.sykmeldingsperioder.let { smp ->
            smp.forEach {
                if (it.tom.isAfter(LocalDate.now())) {
                    sikkerLogger.warn("Skjema fra orgnr ${skjema.avsender.orgnr} har sykemeldingsperiode med tom-dato fram i tid")
                }
            }
        }
    }

    override fun Steg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@LagreSelvbestemtImService),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
        )
}

fun tilInntektsmelding(
    skjema: SkjemaInntektsmeldingSelvbestemt,
    orgNavn: String,
    sykmeldtNavn: String,
    avsenderNavn: String,
): Inntektsmelding {
    val aarsakInnsending =
        if (skjema.selvbestemtId == null) {
            AarsakInnsending.Ny
        } else {
            AarsakInnsending.Endring
        }

    return Inntektsmelding(
        id = UUID.randomUUID(),
        type =
            Inntektsmelding.Type.Selvbestemt(
                id = skjema.selvbestemtId ?: UUID.randomUUID(),
            ),
        sykmeldt =
            Sykmeldt(
                fnr = skjema.sykmeldtFnr,
                navn = sykmeldtNavn,
            ),
        avsender =
            Avsender(
                orgnr = skjema.avsender.orgnr,
                orgNavn = orgNavn,
                navn = avsenderNavn,
                tlf = skjema.avsender.tlf,
            ),
        sykmeldingsperioder = skjema.sykmeldingsperioder,
        agp = skjema.agp,
        inntekt = skjema.inntekt,
        refusjon = skjema.refusjon,
        aarsakInnsending = aarsakInnsending,
        mottatt = OffsetDateTime.now(),
    )
}
