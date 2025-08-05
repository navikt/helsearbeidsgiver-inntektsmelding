package no.nav.helsearbeidsgiver.inntektsmelding.selvbestemtlagreimservice

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Avsender
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Sykmeldt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.ArbeidsforholdType
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmeldingSelvbestemt
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.PeriodeAapen
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.ansettelsesperioderSerializer
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.orgMapSerializer
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.Service
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceMed3Steg
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.date.toOffsetDateTimeOslo
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateTimeSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.collections.orEmpty

data class Steg0(
    val kontekstId: UUID,
    val skjema: SkjemaInntektsmeldingSelvbestemt,
    val avsenderFnr: Fnr,
    val mottatt: LocalDateTime,
)

sealed class Steg1 {
    data class Komplett(
        val orgnrMedNavn: Map<Orgnr, String>,
        val personer: Map<Fnr, Person>,
        val ansettelsesperioder: Map<Orgnr, Set<PeriodeAapen>>,
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

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            kontekstId = Key.KONTEKST_ID.les(UuidSerializer, melding),
            skjema = Key.SKJEMA_INNTEKTSMELDING.les(SkjemaInntektsmeldingSelvbestemt.serializer(), melding),
            avsenderFnr = Key.ARBEIDSGIVER_FNR.les(Fnr.serializer(), melding),
            mottatt = Key.MOTTATT.les(LocalDateTimeSerializer, melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 {
        val orgnrMedNavn = runCatching { Key.VIRKSOMHETER.les(orgMapSerializer, melding) }
        val personer = runCatching { Key.PERSONER.les(personMapSerializer, melding) }
        val ansettelsesperioder = runCatching { Key.ANSETTELSESPERIODER.les(ansettelsesperioderSerializer, melding) }

        val results = listOf(orgnrMedNavn, personer, ansettelsesperioder)

        return if (results.all { it.isSuccess }) {
            Steg1.Komplett(
                orgnrMedNavn = orgnrMedNavn.getOrThrow(),
                personer = personer.getOrThrow(),
                ansettelsesperioder = ansettelsesperioder.getOrThrow(),
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

    override fun utfoerSteg0(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
    ) {
        val svarKafkaKey = KafkaKey(steg0.skjema.sykmeldtFnr)

        kontrollerSkjema(steg0.skjema)

        rapid.publish(
            key = steg0.skjema.sykmeldtFnr,
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to BehovType.HENT_VIRKSOMHET_NAVN.toJson(),
            Key.KONTEKST_ID to steg0.kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.SVAR_KAFKA_KEY to svarKafkaKey.toJson(),
                    Key.ORGNR_UNDERENHETER to setOf(steg0.skjema.avsender.orgnr).toJson(Orgnr.serializer()),
                ).toJson(),
        )

        rapid.publish(
            key = steg0.skjema.sykmeldtFnr,
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to BehovType.HENT_PERSONER.toJson(),
            Key.KONTEKST_ID to steg0.kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.SVAR_KAFKA_KEY to svarKafkaKey.toJson(),
                    Key.FNR_LISTE to
                        setOf(
                            steg0.skjema.sykmeldtFnr,
                            steg0.avsenderFnr,
                        ).toJson(Fnr.serializer()),
                ).toJson(),
        )

        rapid.publish(
            key = steg0.skjema.sykmeldtFnr,
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to BehovType.HENT_ANSETTELSESPERIODER.toJson(),
            Key.KONTEKST_ID to steg0.kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.SVAR_KAFKA_KEY to svarKafkaKey.toJson(),
                    Key.FNR to
                        steg0.skjema.sykmeldtFnr.verdi
                            .toJson(),
                ).toJson(),
        )
    }

    override fun utfoerSteg1(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
    ) {
        if (steg1 is Steg1.Komplett) {
            val sykmeldtNavn = steg1.personer[steg0.skjema.sykmeldtFnr]?.navn.orEmpty()
            val avsenderNavn = steg1.personer[steg0.avsenderFnr]?.navn.orEmpty()
            val orgNavn = steg1.orgnrMedNavn[steg0.skjema.avsender.orgnr] ?: "Ukjent virksomhet"

            val inntektsmelding =
                tilInntektsmelding(
                    skjema = steg0.skjema,
                    orgNavn = orgNavn,
                    sykmeldtNavn = sykmeldtNavn,
                    avsenderNavn = avsenderNavn,
                    mottatt = steg0.mottatt,
                )

            val sykeperioder =
                steg0.skjema.agp
                    ?.perioder
                    .orEmpty()
                    .plus(steg0.skjema.sykmeldingsperioder)

            val ansettelsesperioderForAktuellOrg = steg1.ansettelsesperioder[steg0.skjema.avsender.orgnr].orEmpty()

            val erAktivtArbeidsforhold = sykeperioder.aktivtArbeidsforholdIPeriode(ansettelsesperioderForAktuellOrg)

            val harIngenArbeidsforhold = inntektsmelding.type is Inntektsmelding.Type.Fisker || inntektsmelding.type is Inntektsmelding.Type.UtenArbeidsforhold

            if (erAktivtArbeidsforhold || harIngenArbeidsforhold) {
                rapid
                    .publish(
                        key = inntektsmelding.type.id,
                        Key.EVENT_NAME to eventName.toJson(),
                        Key.BEHOV to BehovType.LAGRE_SELVBESTEMT_IM.toJson(),
                        Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                        Key.DATA to
                            data
                                .plus(
                                    Key.SELVBESTEMT_INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
                                ).toJson(),
                    ).also {
                        logger.info("Publiserte melding med behov '${BehovType.LAGRE_SELVBESTEMT_IM}'.")
                        sikkerLogger.info("Publiserte melding:\n${it.toPretty()}")
                    }
            } else {
                "Mangler arbeidsforhold i perioden".also { feilmelding ->
                    logger.warn(feilmelding)
                    sikkerLogger.warn(feilmelding)
                    val resultJson = ResultJson(failure = feilmelding.toJson())
                    redisStore.skrivResultat(steg0.kontekstId, resultJson)
                }
            }
        }
    }

    override fun utfoerSteg2(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
    ) {
        when (steg2.inntektsmelding.aarsakInnsending) {
            AarsakInnsending.Endring -> {
                utfoerSteg3(data, steg0, steg1, steg2, Steg3(sakId = null))
            }

            AarsakInnsending.Ny -> {
                rapid
                    .publish(
                        key = steg2.inntektsmelding.type.id,
                        Key.EVENT_NAME to eventName.toJson(),
                        Key.BEHOV to BehovType.OPPRETT_SELVBESTEMT_SAK.toJson(),
                        Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                        Key.DATA to
                            data
                                .plus(
                                    Key.SELVBESTEMT_INNTEKTSMELDING to steg2.inntektsmelding.toJson(Inntektsmelding.serializer()),
                                ).toJson(),
                    ).also {
                        logger.info("Publiserte melding med behov '${BehovType.OPPRETT_SELVBESTEMT_SAK}'.")
                        sikkerLogger.info("Publiserte melding:\n${it.toPretty()}")
                    }
            }
        }
    }

    override fun utfoerSteg3(
        data: Map<Key, JsonElement>,
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
                )
            redisStore.skrivResultat(steg0.kontekstId, resultJson)

            if (!steg2.erDuplikat) {
                val publisert =
                    rapid.publish(
                        key = steg2.inntektsmelding.type.id,
                        Key.EVENT_NAME to EventName.SELVBESTEMT_IM_LAGRET.toJson(),
                        Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                        Key.DATA to
                            mapOf(
                                Key.SELVBESTEMT_INNTEKTSMELDING to steg2.inntektsmelding.toJson(Inntektsmelding.serializer()),
                            ).toJson(),
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
            Log.kontekstId(fail.kontekstId),
        ) {
            val utloesendeBehov = Key.BEHOV.lesOrNull(BehovType.serializer(), fail.utloesendeMelding)
            val datafeil =
                when (utloesendeBehov) {
                    BehovType.HENT_VIRKSOMHET_NAVN -> Key.VIRKSOMHETER to emptyMap<String, String>().toJson()

                    // Lesing av personer bruker allerede defaults, så trenger bare map-struktur her
                    BehovType.HENT_PERSONER -> Key.PERSONER to emptyMap<Fnr, Person>().toJson(personMapSerializer)

                    else -> null
                }

            if (datafeil != null) {
                redisStore.skrivMellomlagring(fail.kontekstId, datafeil.first, datafeil.second)

                val meldingMedDefault = mapOf(datafeil) + melding

                onData(meldingMedDefault)
            } else {
                val resultJson = ResultJson(failure = fail.feilmelding.toJson())
                redisStore.skrivResultat(fail.kontekstId, resultJson)
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
            Log.kontekstId(kontekstId),
        )
}

fun ArbeidsforholdType.tilVedtaksperiodeId(): UUID? =
    when (this) {
        is ArbeidsforholdType.MedArbeidsforhold -> vedtaksperiodeId
        else -> null
    }

fun ArbeidsforholdType.tilInntektsmeldingType(id: UUID): Inntektsmelding.Type =
    when (this) {
        is ArbeidsforholdType.MedArbeidsforhold -> Inntektsmelding.Type.Selvbestemt(id = id)
        is ArbeidsforholdType.Fisker -> Inntektsmelding.Type.Fisker(id = id)
        is ArbeidsforholdType.UtenArbeidsforhold -> Inntektsmelding.Type.UtenArbeidsforhold(id = id)
    }

fun tilInntektsmelding(
    skjema: SkjemaInntektsmeldingSelvbestemt,
    orgNavn: String,
    sykmeldtNavn: String,
    avsenderNavn: String,
    mottatt: LocalDateTime,
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
            skjema.arbeidsforholdType.tilInntektsmeldingType(
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
        mottatt = mottatt.toOffsetDateTimeOslo(),
        // TODO: Fjerne "?: skjema.vedtaksperiodeId" etter frontend har implementert arbeidsforholdType
        vedtaksperiodeId = skjema.arbeidsforholdType.tilVedtaksperiodeId() ?: skjema.vedtaksperiodeId,
    )
}
