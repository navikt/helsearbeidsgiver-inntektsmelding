package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.Service
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceMed1Steg
import no.nav.helsearbeidsgiver.felles.utils.Log
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
    val skjema: SkjemaInntektsmelding,
)

data class Steg1(
    val erDuplikat: Boolean,
)

class InnsendingService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore,
) : ServiceMed1Steg<Steg0, Steg1>(),
    Service.MedRedis {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.INSENDING_STARTED

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            transaksjonId = Key.UUID.les(UuidSerializer, melding),
            forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding),
            avsenderFnr = Key.ARBEIDSGIVER_FNR.les(Fnr.serializer(), melding),
            skjema = Key.SKJEMA_INNTEKTSMELDING.les(SkjemaInntektsmelding.serializer(), melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            erDuplikat = Key.ER_DUPLIKAT_IM.les(Boolean.serializer(), melding),
        )

    override fun utfoerSteg0(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
    ) {
        rapid
            .publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.LAGRE_IM_SKJEMA.toJson(),
                Key.UUID to steg0.transaksjonId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                        Key.SKJEMA_INNTEKTSMELDING to steg0.skjema.toJson(SkjemaInntektsmelding.serializer()),
                    ).toJson(),
            ).also { loggBehovPublisert(BehovType.LAGRE_IM_SKJEMA, it) }
    }

    override fun utfoerSteg1(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
    ) {
        val resultJson =
            ResultJson(
                success = steg0.skjema.toJson(SkjemaInntektsmelding.serializer()),
            ).toJson(ResultJson.serializer())

        redisStore.set(RedisKey.of(steg0.transaksjonId), resultJson)

        if (!steg1.erDuplikat) {
            val publisert =
                rapid
                    .publish(
                        Key.EVENT_NAME to EventName.INNTEKTSMELDING_SKJEMA_LAGRET.toJson(),
                        Key.UUID to steg0.transaksjonId.toJson(),
                        Key.DATA to
                            mapOf(
                                Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                                Key.ARBEIDSGIVER_FNR to steg0.avsenderFnr.toJson(),
                                Key.SKJEMA_INNTEKTSMELDING to steg0.skjema.toJson(SkjemaInntektsmelding.serializer()),
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

    override fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    ) {
        val resultJson = ResultJson(failure = fail.feilmelding.toJson())

        redisStore.set(RedisKey.of(fail.transaksjonId), resultJson.toJson(ResultJson.serializer()))
    }
}
