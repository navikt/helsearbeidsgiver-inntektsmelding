package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStoreClassSpecific
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.Service
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

private const val AVSENDER_NAV_NO = "NAV_NO"

class SpinnService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStoreClassSpecific,
) : Service() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

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

    override fun onData(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId),
        ) {
            if (isFinished(melding)) {
                val eksternInntektsmelding = Key.EKSTERN_INNTEKTSMELDING.lesOrNull(EksternInntektsmelding.serializer(), melding)
                if (
                    eksternInntektsmelding != null &&
                    eksternInntektsmelding.avsenderSystemNavn != AVSENDER_NAV_NO
                ) {
                    rapid
                        .publish(
                            Key.EVENT_NAME to EventName.EKSTERN_INNTEKTSMELDING_MOTTATT.toJson(),
                            Key.BEHOV to BehovType.LAGRE_EKSTERN_INNTEKTSMELDING.toJson(),
                            Key.UUID to transaksjonId.toJson(),
                            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                            Key.EKSTERN_INNTEKTSMELDING to eksternInntektsmelding.toJson(EksternInntektsmelding.serializer()),
                        ).also {
                            MdcUtils.withLogFields(
                                Log.event(EventName.EKSTERN_INNTEKTSMELDING_MOTTATT),
                                Log.behov(BehovType.LAGRE_EKSTERN_INNTEKTSMELDING),
                            ) {
                                logger.info("Publiserte melding om ${BehovType.LAGRE_EKSTERN_INNTEKTSMELDING.name}.")
                                sikkerLogger.info("Publiserte melding:\n${it.toPretty()}")
                            }
                        }
                }

                sikkerLogger.info("$eventName fullført.")
            } else {
                val spinnImId = Key.SPINN_INNTEKTSMELDING_ID.les(UuidSerializer, melding)

                rapid
                    .publish(
                        Key.EVENT_NAME to eventName.toJson(),
                        Key.BEHOV to BehovType.HENT_EKSTERN_INNTEKTSMELDING.toJson(),
                        Key.UUID to transaksjonId.toJson(),
                        Key.DATA to
                            mapOf(
                                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                                Key.SPINN_INNTEKTSMELDING_ID to spinnImId.toJson(),
                            ).toJson(),
                    ).also {
                        MdcUtils.withLogFields(
                            Log.behov(BehovType.HENT_EKSTERN_INNTEKTSMELDING),
                        ) {
                            logger.info("Publiserte melding om ${BehovType.HENT_EKSTERN_INNTEKTSMELDING.name}.")
                            sikkerLogger.info("Publiserte melding:\n${it.toPretty()}.")
                        }
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
            sikkerLogger.error("$eventName terminert.")
        }
    }
}
