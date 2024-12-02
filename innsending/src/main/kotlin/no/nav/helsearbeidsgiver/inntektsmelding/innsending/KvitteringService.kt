package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.domene.InnsendtInntektsmelding
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceMed1Steg
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class KvitteringService(
    private val rapid: RapidsConnection,
    private val redisStore: RedisStore,
) : ServiceMed1Steg<KvitteringService.Steg0, KvitteringService.Steg1>() {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.KVITTERING_REQUESTED

    data class Steg0(
        val transaksjonId: UUID,
        val forespoerselId: UUID,
    )

    data class Steg1(
        val inntektsmeldingDokument: Inntektsmelding?,
        val eksternInntektsmelding: EksternInntektsmelding?,
    )

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            transaksjonId = Key.KONTEKST_ID.les(UuidSerializer, melding),
            forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            inntektsmeldingDokument =
                Key.LAGRET_INNTEKTSMELDING
                    .les(ResultJson.serializer(), melding)
                    .success
                    ?.fromJson(Inntektsmelding.serializer()),
            eksternInntektsmelding =
                Key.EKSTERN_INNTEKTSMELDING
                    .les(ResultJson.serializer(), melding)
                    .success
                    ?.fromJson(EksternInntektsmelding.serializer()),
        )

    override fun utfoerSteg0(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
    ) {
        val publisert =
            rapid.publish(
                key = steg0.forespoerselId,
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_LAGRET_IM.toJson(),
                Key.KONTEKST_ID to steg0.transaksjonId.toJson(),
                Key.DATA to
                    data
                        .plus(
                            Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                        ).toJson(),
            )

        MdcUtils.withLogFields(
            Log.behov(BehovType.HENT_LAGRET_IM),
        ) {
            "Publiserte melding med behov ${BehovType.HENT_LAGRET_IM}.".let {
                logger.info(it)
                sikkerLogger.info("$it\n${publisert.toPretty()}")
            }
        }
    }

    override fun utfoerSteg1(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
    ) {
        val resultJson =
            ResultJson(
                success =
                    InnsendtInntektsmelding(steg1.inntektsmeldingDokument, steg1.eksternInntektsmelding).toJson(InnsendtInntektsmelding.serializer()),
            )

        redisStore.skrivResultat(steg0.transaksjonId, resultJson)
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
            "Klarte ikke hente kvittering for forespørsel '${fail.forespoerselId}'.".also {
                logger.warn(it)
                sikkerLogger.warn(it)
            }

            val resultJson = ResultJson(failure = fail.feilmelding.toJson())

            redisStore.skrivResultat(fail.transaksjonId, resultJson)
        }
    }

    override fun Steg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@KvitteringService),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId),
        )
}
