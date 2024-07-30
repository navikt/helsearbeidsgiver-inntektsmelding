package no.nav.helsearbeidsgiver.inntektsmelding.innsending

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
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceMed3Steg
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

data class Steg0(
    val transaksjonId: UUID,
    val forespoerselId: UUID,
    val avsenderFnr: Fnr,
    val skjema: JsonElement,
)

data class Steg1(
    val forespoersel: Forespoersel,
)

sealed class Steg2 {
    data class Komplett(
        val aarsakInnsending: AarsakInnsending,
        val orgNavn: String,
        val sykmeldt: PersonDato,
        val avsender: PersonDato,
    ) : Steg2()

    data object Delvis : Steg2()
}

data class Steg3(
    val inntektsmelding: Inntektsmelding,
    val erDuplikat: Boolean,
)

class InnsendingService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore,
) : ServiceMed3Steg<Steg0, Steg1, Steg2, Steg3>() {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.INSENDING_STARTED
    override val startKeys =
        setOf(
            Key.FORESPOERSEL_ID,
            Key.ARBEIDSGIVER_ID,
            Key.SKJEMA_INNTEKTSMELDING,
        )
    override val dataKeys =
        setOf(
            Key.VIRKSOMHET,
            Key.ARBEIDSGIVER_INFORMASJON,
            Key.ARBEIDSTAKER_INFORMASJON,
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
            avsenderFnr = Key.ARBEIDSGIVER_ID.les(Fnr.serializer(), melding),
            skjema = Key.SKJEMA_INNTEKTSMELDING.les(JsonElement.serializer(), melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            forespoersel = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding),
        )

    override fun lesSteg2(melding: Map<Key, JsonElement>): Steg2 {
        val tidligereInntektsmelding = runCatching { Key.LAGRET_INNTEKTSMELDING.les(ResultJson.serializer(), melding) }
        val tidligereEksternInntektsmelding = runCatching { Key.EKSTERN_INNTEKTSMELDING.les(ResultJson.serializer(), melding) }
        val orgNavn = runCatching { Key.VIRKSOMHET.les(String.serializer(), melding) }
        val sykmeldt = runCatching { Key.ARBEIDSTAKER_INFORMASJON.les(PersonDato.serializer(), melding) }
        val avsender = runCatching { Key.ARBEIDSGIVER_INFORMASJON.les(PersonDato.serializer(), melding) }

        val results = listOf(tidligereInntektsmelding, tidligereEksternInntektsmelding, orgNavn, sykmeldt, avsender)

        return if (results.all { it.isSuccess }) {
            val aarsakInnsending =
                if (tidligereInntektsmelding.getOrThrow().success == null && tidligereEksternInntektsmelding.getOrThrow().success == null) {
                    AarsakInnsending.Ny
                } else {
                    AarsakInnsending.Endring
                }

            Steg2.Komplett(
                aarsakInnsending = aarsakInnsending,
                orgNavn = orgNavn.getOrThrow(),
                sykmeldt = sykmeldt.getOrThrow(),
                avsender = avsender.getOrThrow(),
            )
        } else if (results.any { it.isSuccess }) {
            Steg2.Delvis
        } else {
            throw results.firstNotNullOf { it.exceptionOrNull() }
        }
    }

    override fun lesSteg3(melding: Map<Key, JsonElement>): Steg3 =
        Steg3(
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

        rapid
            .publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_VIRKSOMHET_NAVN.toJson(),
                Key.UUID to steg0.transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                Key.ORGNRUNDERENHET to steg1.forespoersel.orgnr.toJson(),
            ).also { loggBehovPublisert(BehovType.HENT_VIRKSOMHET_NAVN, it) }

        rapid
            .publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.FULLT_NAVN.toJson(),
                Key.UUID to steg0.transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                Key.IDENTITETSNUMMER to steg1.forespoersel.fnr.toJson(),
                Key.ARBEIDSGIVER_ID to steg0.avsenderFnr.toJson(),
            ).also { loggBehovPublisert(BehovType.FULLT_NAVN, it) }
    }

    override fun utfoerSteg2(
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
    ) {
        if (steg2 is Steg2.Komplett) {
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

            val inntektsmelding =
                mapInntektsmelding(
                    forespoersel = steg1.forespoersel,
                    skjema = skjema,
                    fulltnavnArbeidstaker = steg2.sykmeldt.navn,
                    virksomhetNavn = steg2.orgNavn,
                    innsenderNavn = steg2.avsender.navn,
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
    }

    override fun utfoerSteg3(
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
        steg3: Steg3,
    ) {
        val resultJson =
            ResultJson(
                success = steg3.inntektsmelding.toJson(Inntektsmelding.serializer()),
            ).toJson(ResultJson.serializer())

        redisStore.set(RedisKey.of(steg0.transaksjonId), resultJson)

        if (!steg3.erDuplikat) {
            val publisert =
                rapid.publish(
                    Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
                    Key.UUID to steg0.transaksjonId.toJson(),
                    Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                    Key.INNTEKTSMELDING_DOKUMENT to steg3.inntektsmelding.toJson(Inntektsmelding.serializer()),
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
                BehovType.HENT_VIRKSOMHET_NAVN -> {
                    listOf(
                        Key.VIRKSOMHET to "Ukjent virksomhet".toJson(),
                    )
                }

                BehovType.FULLT_NAVN -> {
                    val sykmeldtFnr = Key.IDENTITETSNUMMER.les(String.serializer(), melding)
                    val avsenderFnr = Key.ARBEIDSGIVER_ID.les(String.serializer(), melding)

                    listOf(
                        Key.ARBEIDSTAKER_INFORMASJON to tomPerson(sykmeldtFnr).toJson(PersonDato.serializer()),
                        Key.ARBEIDSGIVER_INFORMASJON to tomPerson(avsenderFnr).toJson(PersonDato.serializer()),
                    )
                }

                else -> {
                    emptyList()
                }
            }

        if (datafeil.isNotEmpty()) {
            datafeil.onEach { (key, defaultVerdi) ->
                redisStore.set(RedisKey.of(fail.transaksjonId, key), defaultVerdi)
            }

            val meldingMedDefault = datafeil.toMap().plus(melding)

            onData(meldingMedDefault)
        } else {
            val resultJson = ResultJson(failure = fail.feilmelding.toJson())

            redisStore.set(RedisKey.of(fail.transaksjonId), resultJson.toJson(ResultJson.serializer()))
        }
    }

    override fun Steg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@InnsendingService),
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

private fun tomPerson(fnr: String): PersonDato =
    PersonDato(
        navn = "",
        fødselsdato = null,
        ident = fnr,
    )
