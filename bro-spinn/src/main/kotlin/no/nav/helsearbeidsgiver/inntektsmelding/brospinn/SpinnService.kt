package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceMed1Steg
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

private const val AVSENDER_NAV_NO = "NAV_NO"

data class Steg0(
    val transaksjonId: UUID,
    val forespoerselId: UUID,
    val spinnImId: UUID,
)

data class Steg1(
    val eksternInntektsmelding: EksternInntektsmelding,
)

class SpinnService(
    private val rapid: RapidsConnection,
) : ServiceMed1Steg<Steg0, Steg1>() {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.EKSTERN_INNTEKTSMELDING_REQUESTED
    override val startKeys =
        setOf(
            Key.FORESPOERSEL_ID,
            Key.SPINN_INNTEKTSMELDING_ID,
        )
    override val dataKeys =
        setOf(
            Key.EKSTERN_INNTEKTSMELDING,
        )

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            transaksjonId = Key.UUID.les(UuidSerializer, melding),
            forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding),
            spinnImId = Key.SPINN_INNTEKTSMELDING_ID.les(UuidSerializer, melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            eksternInntektsmelding = Key.EKSTERN_INNTEKTSMELDING.les(EksternInntektsmelding.serializer(), melding),
        )

    override fun utfoerSteg0(steg0: Steg0) {
        val publisert =
            rapid.publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_EKSTERN_INNTEKTSMELDING.toJson(),
                Key.UUID to steg0.transaksjonId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                        Key.SPINN_INNTEKTSMELDING_ID to steg0.spinnImId.toJson(),
                    ).toJson(),
            )

        MdcUtils.withLogFields(
            Log.behov(BehovType.HENT_EKSTERN_INNTEKTSMELDING),
        ) {
            logger.info("Publiserte melding om ${BehovType.HENT_EKSTERN_INNTEKTSMELDING}.")
            sikkerLogger.info("Publiserte melding:\n${publisert.toPretty()}.")
        }
    }

    override fun utfoerSteg1(
        steg0: Steg0,
        steg1: Steg1,
    ) {
        if (steg1.eksternInntektsmelding.avsenderSystemNavn != AVSENDER_NAV_NO) {
            val publisert =
                rapid.publish(
                    Key.EVENT_NAME to EventName.EKSTERN_INNTEKTSMELDING_MOTTATT.toJson(),
                    Key.BEHOV to BehovType.LAGRE_EKSTERN_INNTEKTSMELDING.toJson(),
                    Key.UUID to steg0.transaksjonId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                            Key.EKSTERN_INNTEKTSMELDING to steg1.eksternInntektsmelding.toJson(EksternInntektsmelding.serializer()),
                        ).toJson(),
                )

            MdcUtils.withLogFields(
                Log.event(EventName.EKSTERN_INNTEKTSMELDING_MOTTATT),
                Log.behov(BehovType.LAGRE_EKSTERN_INNTEKTSMELDING),
            ) {
                logger.info("Publiserte melding om ${BehovType.LAGRE_EKSTERN_INNTEKTSMELDING}.")
                sikkerLogger.info("Publiserte melding:\n${publisert.toPretty()}")
            }
        }

        sikkerLogger.info("$eventName fullført.")
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
            sikkerLogger.error("$eventName terminert.")
        }
    }

    override fun Steg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@SpinnService),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId),
        )
}
