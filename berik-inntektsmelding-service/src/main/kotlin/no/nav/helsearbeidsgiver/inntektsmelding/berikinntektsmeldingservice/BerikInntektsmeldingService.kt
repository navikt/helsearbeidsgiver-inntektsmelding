package no.nav.helsearbeidsgiver.inntektsmelding.berikinntektsmeldingservice

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Utils.convert
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Forespoersel
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
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.Service
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceMed5Steg
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

private const val UKJENT_NAVN = "Ukjent navn"

data class Steg0(
    val transaksjonId: UUID,
    val forespoerselId: UUID,
    val avsenderFnr: Fnr,
    val skjema: JsonElement,
)

data class Steg1(
    val forespoersel: Forespoersel,
)

data class Steg2(
    val aarsakInnsending: AarsakInnsending,
)

data class Steg3(
    val orgNavn: String,
)

data class Steg4(
    val personer: Map<Fnr, Person>,
)

data class Steg5(
    val inntektsmelding: Inntektsmelding,
    val erDuplikat: Boolean,
)

class BerikInntektsmeldingService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore,
) : ServiceMed5Steg<Steg0, Steg1, Steg2, Steg3, Steg4, Steg5>(),
    Service.MedRedis {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.INNTEKTSMELDING_SKJEMA_LAGRET
    override val startKeys =
        setOf(
            Key.FORESPOERSEL_ID,
            Key.ARBEIDSGIVER_FNR,
            Key.SKJEMA_INNTEKTSMELDING,
        )
    override val dataKeys =
        setOf(
            Key.VIRKSOMHET,
            Key.PERSONER,
            Key.INNTEKTSMELDING_DOKUMENT,
            Key.ER_DUPLIKAT_IM,
            Key.FORESPOERSEL_SVAR,
            Key.LAGRET_INNTEKTSMELDING,
            Key.EKSTERN_INNTEKTSMELDING,
        )

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            transaksjonId = Key.UUID.les(UuidSerializer, melding),
            forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding),
            avsenderFnr = Key.ARBEIDSGIVER_FNR.les(Fnr.serializer(), melding),
            skjema = Key.SKJEMA_INNTEKTSMELDING.les(JsonElement.serializer(), melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            forespoersel = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding),
        )

    override fun lesSteg2(melding: Map<Key, JsonElement>): Steg2 {
        val tidligereInntektsmelding = runCatching { Key.LAGRET_INNTEKTSMELDING.les(ResultJson.serializer(), melding) }
        val tidligereEksternInntektsmelding = runCatching { Key.EKSTERN_INNTEKTSMELDING.les(ResultJson.serializer(), melding) }

        val aarsakInnsending =
            if (tidligereInntektsmelding.getOrThrow().success == null && tidligereEksternInntektsmelding.getOrThrow().success == null) {
                AarsakInnsending.Ny
            } else {
                AarsakInnsending.Endring
            }

        return Steg2(
            aarsakInnsending = aarsakInnsending,
        )
    }

    override fun lesSteg3(melding: Map<Key, JsonElement>): Steg3 =
        Steg3(
            orgNavn = Key.VIRKSOMHET.les(String.serializer(), melding),
        )

    override fun lesSteg4(melding: Map<Key, JsonElement>): Steg4 =
        Steg4(
            personer = Key.PERSONER.les(personMapSerializer, melding),
        )

    override fun lesSteg5(melding: Map<Key, JsonElement>): Steg5 =
        Steg5(
            inntektsmelding = Key.INNTEKTSMELDING_DOKUMENT.les(Inntektsmelding.serializer(), melding),
            erDuplikat = Key.ER_DUPLIKAT_IM.les(Boolean.serializer(), melding),
        )

    override fun utfoerSteg0(steg0: Steg0) {
        rapid
            .publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                Key.UUID to steg0.transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
            ).also { loggBehovPublisert(BehovType.HENT_TRENGER_IM, it) }
    }

    override fun utfoerSteg1(
        steg0: Steg0,
        steg1: Steg1,
    ) {
        rapid
            .publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_LAGRET_IM.toJson(),
                Key.UUID to steg0.transaksjonId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                    ).toJson(),
            ).also { loggBehovPublisert(BehovType.HENT_LAGRET_IM, it) }
    }

    override fun utfoerSteg2(
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
    ) {
        rapid
            .publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_VIRKSOMHET_NAVN.toJson(),
                Key.UUID to steg0.transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                Key.ORGNRUNDERENHET to steg1.forespoersel.orgnr.toJson(),
            ).also { loggBehovPublisert(BehovType.HENT_VIRKSOMHET_NAVN, it) }
    }

    override fun utfoerSteg3(
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
        steg3: Steg3,
    ) {
        rapid
            .publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_PERSONER.toJson(),
                Key.UUID to steg0.transaksjonId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                        Key.FNR_LISTE to
                            listOf(
                                steg1.forespoersel.fnr.let(::Fnr),
                                steg0.avsenderFnr,
                            ).toJson(Fnr.serializer()),
                    ).toJson(),
            ).also { loggBehovPublisert(BehovType.HENT_PERSONER, it) }
    }

    override fun utfoerSteg4(
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
        steg3: Steg3,
        steg4: Steg4,
    ) {
        val skjema =
            runCatching {
                steg0.skjema
                    .fromJson(SkjemaInntektsmelding.serializer())
                    .convert(
                        sykmeldingsperioder = steg1.forespoersel.sykmeldingsperioder,
                        aarsakInnsending = steg2.aarsakInnsending,
                    )
            }.getOrElse {
                steg0.skjema.fromJson(Innsending.serializer())
            }

        val sykmeldtNavn = steg4.personer[steg1.forespoersel.fnr.let(::Fnr)]?.navn ?: UKJENT_NAVN
        val avsenderNavn = steg4.personer[steg0.avsenderFnr]?.navn ?: UKJENT_NAVN

        val inntektsmelding =
            mapInntektsmelding(
                forespoersel = steg1.forespoersel,
                skjema = skjema,
                fulltnavnArbeidstaker = sykmeldtNavn,
                virksomhetNavn = steg3.orgNavn,
                innsenderNavn = avsenderNavn,
            )

        if (inntektsmelding.bestemmendeFraværsdag.isBefore(inntektsmelding.inntektsdato)) {
            "Bestemmende fraværsdag er før inntektsdato. Dette er ikke mulig. Spleis vil trolig spør om ny inntektsmelding.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
        }

        rapid
            .publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.PERSISTER_IM.toJson(),
                Key.UUID to steg0.transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                Key.INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
            ).also { loggBehovPublisert(BehovType.PERSISTER_IM, it) }
    }

    override fun utfoerSteg5(
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
        steg3: Steg3,
        steg4: Steg4,
        steg5: Steg5,
    ) {
        val resultJson =
            ResultJson(
                success = steg5.inntektsmelding.toJson(Inntektsmelding.serializer()),
            ).toJson(ResultJson.serializer())

        redisStore.set(RedisKey.of(steg0.transaksjonId), resultJson)

        if (!steg5.erDuplikat) {
            val publisert =
                rapid.publish(
                    Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
                    Key.UUID to steg0.transaksjonId.toJson(),
                    Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                    Key.INNTEKTSMELDING_DOKUMENT to steg5.inntektsmelding.toJson(Inntektsmelding.serializer()),
                )

            MdcUtils.withLogFields(
                Log.event(EventName.INNTEKTSMELDING_MOTTATT),
            ) {
                logger.info("Publiserte melding.")
                sikkerLogger.info("Publiserte melding:\n${publisert.toPretty()}")
            }
        }
    }

    override fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    ) {
        val utloesendeBehov = Key.BEHOV.lesOrNull(BehovType.serializer(), fail.utloesendeMelding.toMap())

        val datafeil =
            when (utloesendeBehov) {
                BehovType.HENT_VIRKSOMHET_NAVN -> Key.VIRKSOMHETER to emptyMap<String, String>().toJson()
                BehovType.HENT_PERSONER -> Key.PERSONER to emptyMap<String, String>().toJson()
                else -> null
            }

        if (datafeil != null) {
            redisStore.set(RedisKey.of(fail.transaksjonId, datafeil.first), datafeil.second)
            val meldingMedDefault = mapOf(datafeil).plus(melding)

            onData(meldingMedDefault)
        } else {
            val resultJson = ResultJson(failure = fail.feilmelding.toJson())

            redisStore.set(RedisKey.of(fail.transaksjonId), resultJson.toJson(ResultJson.serializer()))
        }
    }

    override fun Steg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@BerikInntektsmeldingService),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId),
        )

    private fun loggBehovPublisert(
        behovType: BehovType,
        publisert: JsonElement,
    ) {
        MdcUtils.withLogFields(
            Log.behov(behovType),
        ) {
            "Publiserte melding med behov $behovType.".let {
                logger.info(it)
                sikkerLogger.info("$it\n${publisert.toPretty()}")
            }
        }
    }
}
