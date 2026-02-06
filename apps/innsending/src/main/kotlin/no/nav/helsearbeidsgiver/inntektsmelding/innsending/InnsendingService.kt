package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.kontrakt.resultat.lagreim.LagreImError
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.Tekst
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.domene.Person
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.personMapSerializer
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.Publisher
import no.nav.hag.simba.utils.rr.service.ServiceMed3Steg
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateTimeSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.time.LocalDateTime
import java.util.UUID
import no.nav.hag.simba.utils.felles.domene.SkjemaInntektsmeldingIntern as SkjemaInntektsmelding

data class Steg0(
    val kontekstId: UUID,
    val skjema: SkjemaInntektsmelding,
    val avsenderFnr: Fnr,
    val mottatt: LocalDateTime,
)

data class Steg1(
    val forespoersel: Forespoersel,
)

data class Steg2(
    val personer: Map<Fnr, Person>,
)

data class Steg3(
    val inntektsmeldingId: UUID,
    val erDuplikat: Boolean,
)

class InnsendingService(
    private val publisher: Publisher,
    private val redisStore: RedisStore,
) : ServiceMed3Steg<Steg0, Steg1, Steg2, Steg3>() {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val initialEventName = EventName.INSENDING_STARTED
    override val serviceEventName = EventName.SERVICE_FORESPURT_IM_LAGRE_SKJEMA

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            kontekstId = Key.KONTEKST_ID.les(UuidSerializer, melding),
            avsenderFnr = Key.ARBEIDSGIVER_FNR.les(Fnr.serializer(), melding),
            skjema = Key.SKJEMA_INNTEKTSMELDING.les(SkjemaInntektsmelding.serializer(), melding),
            mottatt = Key.MOTTATT.les(LocalDateTimeSerializer, melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            forespoersel = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding),
        )

    override fun lesSteg2(melding: Map<Key, JsonElement>): Steg2 =
        Steg2(
            personer = Key.PERSONER.les(personMapSerializer, melding),
        )

    override fun lesSteg3(melding: Map<Key, JsonElement>): Steg3 =
        Steg3(
            inntektsmeldingId = Key.INNTEKTSMELDING_ID.les(UuidSerializer, melding),
            erDuplikat = Key.ER_DUPLIKAT_IM.les(Boolean.serializer(), melding),
        )

    override fun utfoerSteg0(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
    ) {
        publisher
            .publish(
                key = steg0.skjema.forespoerselId,
                Key.EVENT_NAME to serviceEventName.toJson(),
                Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                Key.DATA to
                    data
                        .plus(Key.FORESPOERSEL_ID to steg0.skjema.forespoerselId.toJson())
                        .toJson(),
            ).also { loggBehovPublisert(BehovType.HENT_TRENGER_IM, it) }
    }

    override fun utfoerSteg1(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
    ) {
        publisher
            .publish(
                key = steg0.skjema.forespoerselId,
                Key.EVENT_NAME to serviceEventName.toJson(),
                Key.BEHOV to BehovType.HENT_PERSONER.toJson(),
                Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                Key.DATA to
                    data
                        .plus(
                            mapOf(
                                Key.SVAR_KAFKA_KEY to KafkaKey(steg0.skjema.forespoerselId).toJson(),
                                Key.FNR_LISTE to setOf(steg0.avsenderFnr).toJson(Fnr.serializer()),
                            ),
                        ).toJson(),
            ).also { loggBehovPublisert(BehovType.HENT_PERSONER, it) }
    }

    override fun utfoerSteg2(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
    ) {
        // Oppretter unik ID for hver inntektsmelding
        val inntektsmeldingId = UUID.randomUUID()
        val avsenderNavn = steg2.personer[steg0.avsenderFnr]?.navn ?: Tekst.UKJENT_NAVN

        val agp = steg0.skjema.agp
        if (agp == null ||
            agp.erGyldigHvisIkkeForespurt(steg1.forespoersel.forespurtData.arbeidsgiverperiode.paakrevd, steg1.forespoersel.sykmeldingsperioder)
        ) {
            publisher
                .publish(
                    key = steg0.skjema.forespoerselId,
                    Key.EVENT_NAME to serviceEventName.toJson(),
                    Key.BEHOV to BehovType.LAGRE_IM_SKJEMA.toJson(),
                    Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                    Key.DATA to
                        data
                            .plus(
                                mapOf(
                                    Key.INNTEKTSMELDING_ID to inntektsmeldingId.toJson(),
                                    Key.AVSENDER_NAVN to avsenderNavn.toJson(),
                                ),
                            ).toJson(),
                ).also { loggBehovPublisert(BehovType.LAGRE_IM_SKJEMA, it) }
        } else {
            "Avviser inntektsmelding som inneholder ikke-forespurt AGP som er ugyldig.".also {
                logger.warn(it)
                sikkerLogger.warn(it)
            }

            val resultJson =
                ResultJson(
                    failure =
                        LagreImError(
                            feiletValidering = "Arbeidsgiverperioden må indikere at sykmeldt arbeidet i starten av sykmeldingsperioden.",
                        ).toJson(LagreImError.serializer()),
                )

            redisStore.skrivResultat(steg0.kontekstId, resultJson)
        }
    }

    override fun utfoerSteg3(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
        steg3: Steg3,
    ) {
        val resultJson =
            ResultJson(
                success = steg0.skjema.forespoerselId.toJson(),
            )

        redisStore.skrivResultat(steg0.kontekstId, resultJson)

        if (!steg3.erDuplikat) {
            val avsenderNavn = steg2.personer[steg0.avsenderFnr]?.navn ?: Tekst.UKJENT_NAVN

            val publisert =
                publisher.publish(
                    key = steg0.skjema.forespoerselId,
                    Key.EVENT_NAME to EventName.INNTEKTSMELDING_SKJEMA_LAGRET.toJson(),
                    Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FORESPOERSEL_SVAR to steg1.forespoersel.toJson(),
                            Key.INNTEKTSMELDING_ID to steg3.inntektsmeldingId.toJson(),
                            Key.SKJEMA_INNTEKTSMELDING to steg0.skjema.toJson(SkjemaInntektsmelding.serializer()),
                            Key.AVSENDER_NAVN to avsenderNavn.toJson(),
                            Key.MOTTATT to steg0.mottatt.toJson(),
                        ).toJson(),
                )

            MdcUtils.withLogFields(
                Log.event(EventName.INNTEKTSMELDING_SKJEMA_LAGRET),
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
        "Klarte ikke lagre inntektsmelding.".also {
            logger.error(it)
            sikkerLogger.error(it)
        }

        val resultJson =
            ResultJson(
                failure = LagreImError().toJson(LagreImError.serializer()),
            )

        redisStore.skrivResultat(fail.kontekstId, resultJson)
    }

    override fun Steg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@InnsendingService),
            Log.event(serviceEventName),
            Log.kontekstId(kontekstId),
            Log.forespoerselId(skjema.forespoerselId),
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
