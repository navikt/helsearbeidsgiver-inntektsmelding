package no.nav.helsearbeidsgiver.inntektsmelding.innsending.api

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Forespoersel
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.model.Fail
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.rr.Publisher
import no.nav.hag.simba.utils.rr.service.ServiceMed2Steg
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.api.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateTimeSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.time.LocalDateTime
import java.util.UUID

data class Steg0(
    val kontekstId: UUID,
    val innsending: Innsending,
    val mottatt: LocalDateTime,
)

data class Steg1(
    val forespoersel: Forespoersel,
)

data class Steg2(
    val inntektsmeldingId: UUID,
    val erDuplikat: Boolean,
)

class ApiInnsendingService(
    private val publisher: Publisher,
    private val redisStore: RedisStore,
) : ServiceMed2Steg<Steg0, Steg1, Steg2>() {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.API_INNSENDING_STARTET

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            kontekstId = Key.KONTEKST_ID.les(UuidSerializer, melding),
            innsending = Key.INNSENDING.les(Innsending.serializer(), melding),
            mottatt = Key.MOTTATT.les(LocalDateTimeSerializer, melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            forespoersel = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding),
        )

    override fun lesSteg2(melding: Map<Key, JsonElement>): Steg2 =
        Steg2(
            inntektsmeldingId = Key.INNTEKTSMELDING_ID.les(UuidSerializer, melding),
            erDuplikat = Key.ER_DUPLIKAT_IM.les(Boolean.serializer(), melding),
        )

    override fun utfoerSteg0(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
    ) {
        publisher
            .publish(
                key = steg0.innsending.skjema.forespoerselId,
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                Key.DATA to
                    data
                        .plus(
                            Key.FORESPOERSEL_ID to
                                steg0.innsending.skjema.forespoerselId
                                    .toJson(),
                        ).toJson(),
            ).also { loggBehovPublisert(BehovType.HENT_TRENGER_IM, it) }
    }

    override fun utfoerSteg1(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
    ) {
        publisher
            .publish(
                key = steg0.innsending.skjema.forespoerselId,
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.LAGRE_IM_SKJEMA.toJson(),
                Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                Key.DATA to
                    data
                        .plus(
                            mapOf(
                                Key.INNTEKTSMELDING_ID to steg0.innsending.innsendingId.toJson(),
                                Key.SKJEMA_INNTEKTSMELDING to steg0.innsending.skjema.toJson(SkjemaInntektsmelding.serializer()),
                            ),
                        ).toJson(),
            ).also { loggBehovPublisert(BehovType.LAGRE_IM_SKJEMA, it) }
    }

    override fun utfoerSteg2(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
    ) {
        val resultJson =
            ResultJson(
                success =
                    steg0.innsending.skjema.forespoerselId
                        .toJson(),
            )

        redisStore.skrivResultat(steg0.kontekstId, resultJson)

        if (!steg2.erDuplikat) {
            val publisert =
                publisher.publish(
                    key = steg0.innsending.skjema.forespoerselId,
                    Key.EVENT_NAME to EventName.INNTEKTSMELDING_SKJEMA_LAGRET.toJson(),
                    Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FORESPOERSEL_SVAR to steg1.forespoersel.toJson(Forespoersel.serializer()),
                            Key.INNTEKTSMELDING_ID to steg2.inntektsmeldingId.toJson(),
                            Key.SKJEMA_INNTEKTSMELDING to steg0.innsending.skjema.toJson(SkjemaInntektsmelding.serializer()), // fjern
                            Key.MOTTATT to steg0.mottatt.toJson(),
                            Key.INNSENDING to steg0.innsending.toJson(Innsending.serializer()),
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
        val resultJson = ResultJson(failure = fail.feilmelding.toJson())

        redisStore.skrivResultat(fail.kontekstId, resultJson)
    }

    override fun Steg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@ApiInnsendingService),
            Log.event(eventName),
            Log.kontekstId(kontekstId),
            Log.inntektsmeldingId(innsending.innsendingId),
            Log.forespoerselId(innsending.skjema.forespoerselId),
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
