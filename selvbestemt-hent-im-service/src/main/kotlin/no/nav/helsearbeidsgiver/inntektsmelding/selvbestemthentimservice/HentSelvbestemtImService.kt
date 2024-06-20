package no.nav.helsearbeidsgiver.inntektsmelding.selvbestemthentimservice

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStoreClassSpecific
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.Service
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class HentSelvbestemtImService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStoreClassSpecific
) : Service() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override val eventName = EventName.SELVBESTEMT_IM_REQUESTED
    override val startKeys = setOf(
        Key.SELVBESTEMT_ID
    )
    override val dataKeys = setOf(
        Key.SELVBESTEMT_INNTEKTSMELDING
    )

    override fun onData(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val selvbestemtId = Key.SELVBESTEMT_ID.les(UuidSerializer, melding)

        MdcUtils.withLogFields(
            Log.selvbestemtId(selvbestemtId)
        ) {
            if (isFinished(melding)) {
                val inntektsmeldingJson = Key.SELVBESTEMT_INNTEKTSMELDING.les(JsonElement.serializer(), melding)
                val resultJson = ResultJson(success = inntektsmeldingJson).toJson(ResultJson.serializer())
                redisStore.set(RedisKey.of(transaksjonId), resultJson)
            } else {
                rapid.publish(
                    Key.EVENT_NAME to eventName.toJson(),
                    Key.BEHOV to BehovType.HENT_SELVBESTEMT_IM.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.SELVBESTEMT_ID to selvbestemtId.toJson()
                )
                    .also { loggBehovPublisert(BehovType.HENT_SELVBESTEMT_IM, it) }
            }
        }
    }

    override fun onError(melding: Map<Key, JsonElement>, fail: Fail) {
        val feilmeldingJson = fail.feilmelding.toJson(String.serializer())
        val resultJson = ResultJson(failure = feilmeldingJson).toJson(ResultJson.serializer())
        redisStore.set(RedisKey.of(fail.transaksjonId), resultJson)
    }

    private fun loggBehovPublisert(behovType: BehovType, publisert: JsonElement) {
        MdcUtils.withLogFields(
            Log.behov(behovType)
        ) {
            "Publiserte melding med behov $behovType.".let {
                logger.info(it)
                sikkerLogger.info("$it\n${publisert.toPretty()}")
            }
        }
    }
}
