package no.nav.helsearbeidsgiver.inntektsmelding.trengerservice

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Tekst
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceMed1Steg
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class HentForespoerslerForVedtaksperiodeIdListeService(
    private val rapid: RapidsConnection,
    private val redisStore: RedisStore,
) : ServiceMed1Steg<HentForespoerslerForVedtaksperiodeIdListeService.Steg0, HentForespoerslerForVedtaksperiodeIdListeService.Steg1>() {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.FORESPOERSLER_REQUESTED

    data class Steg0(
        val transaksjonId: UUID,
        val vedtaksperiodeIdListe: List<UUID>,
    )

    data class Steg1(
        val forespoersler: Map<UUID, Forespoersel>,
    )

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            transaksjonId = Key.UUID.les(UuidSerializer, melding),
            vedtaksperiodeIdListe = Key.VEDTAKSPERIODE_ID_LISTE.les(UuidSerializer.list(), melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            forespoersler = Key.FORESPOERSEL_MAP.les(serializer = MapSerializer(UuidSerializer, Forespoersel.serializer()), melding = melding),
        )

    override fun utfoerSteg0(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
    ) {
        rapid
            .publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE.toJson(),
                Key.UUID to steg0.transaksjonId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.VEDTAKSPERIODE_ID_LISTE to steg0.vedtaksperiodeIdListe.toJson(UuidSerializer),
                    ).toJson(),
            ).also { loggBehovPublisert(BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE, it) }
    }

    override fun utfoerSteg1(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
    ) {
        val resultJson =
            ResultJson(
                success = steg1.forespoersler.toJson(MapSerializer(UuidSerializer, Forespoersel.serializer())),
            ).toJson(ResultJson.serializer())

        redisStore.set(RedisKey.of(steg0.transaksjonId), resultJson)
    }

    override fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    ) {
        "Uoverkommelig feil oppsto under henting av forespørsler fra vedtaksperiode-IDer".also {
            logger.warn(it)
            sikkerLogger.warn(it)
        }

        val resultJson =
            ResultJson(
                failure = Tekst.TEKNISK_FEIL_FORBIGAAENDE.toJson(),
            ).toJson(ResultJson.serializer())

        redisStore.set(RedisKey.of(fail.transaksjonId), resultJson)
    }

    override fun Steg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@HentForespoerslerForVedtaksperiodeIdListeService),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
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
