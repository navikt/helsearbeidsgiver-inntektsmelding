package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Innsending
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
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class InnsendingService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStoreClassSpecific
) : Service() {
    private val logger = logger()

    override val eventName = EventName.INSENDING_STARTED
    override val startKeys = setOf(
        Key.FORESPOERSEL_ID,
        Key.ORGNRUNDERENHET,
        Key.IDENTITETSNUMMER,
        Key.ARBEIDSGIVER_ID,
        Key.SKJEMA_INNTEKTSMELDING,
        Key.CLIENT_ID
    )
    override val dataKeys = setOf(
        Key.ER_DUPLIKAT_IM,
        Key.PERSISTERT_SKJEMA_INNTEKTSMELDING
    )

    override fun onData(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val startdata = lesStartdata(melding)

        when {
            isFinished(melding) -> onFinished(melding, transaksjonId, startdata)

            isOnStep0(melding) -> onStep0(melding, transaksjonId, startdata)

            else -> logger.info("Noe gikk galt") // TODO: Hva gjør vi her?
        }
    }

    private fun onStep0(
        melding: Map<Key, JsonElement>,
        transaksjonId: UUID,
        startData: Array<Pair<Key, JsonElement>>
    ) {
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)
        val skjema = Key.SKJEMA_INNTEKTSMELDING.les(Innsending.serializer(), melding)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId)
        ) {
            rapid.publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.PERSISTER_IM_SKJEMA.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.SKJEMA_INNTEKTSMELDING to skjema.toJson(Innsending.serializer()),
                *startData
            )
        }
    }

    private fun onFinished(
        melding: Map<Key, JsonElement>,
        transaksjonId: UUID,
        startData: Array<Pair<Key, JsonElement>>
    ) {
        val erDuplikat = Key.ER_DUPLIKAT_IM.les(Boolean.serializer(), melding)
        val skjema = Key.SKJEMA_INNTEKTSMELDING.les(Innsending.serializer(), melding)
        val clientId = Key.CLIENT_ID.les(UuidSerializer, melding)

        logger.info("Publiserer skjema inntektsmelding svar med clientId $clientId til redis")
        val resultJson = ResultJson(success = skjema.toJson(Innsending.serializer())).toJson(ResultJson.serializer())
        redisStore.set(RedisKey.of(clientId), resultJson)

        if (!erDuplikat) {
            logger.info("Publiserer INNTEKTSMELDING_SKJEMA_LAGRET under uuid $transaksjonId")
            logger.info("InnsendingService: emitting event INNTEKTSMELDING_SKJEMA_LAGRET")
            rapid.publish(
                Key.EVENT_NAME to EventName.INNTEKTSMELDING_SKJEMA_LAGRET.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.DATA to "".toJson(),
                *startData
            )
                .also {
                    logger.info("Submitting INNTEKTSMELDING_SKJEMA_LAGRET")
                    sikkerLogger.info("Submitting INNTEKTSMELDING_SKJEMA_LAGRET ${it.toPretty()}")
                }
        }
    }

    override fun onError(melding: Map<Key, JsonElement>, fail: Fail) {
        val clientId = Key.CLIENT_ID.les(UuidSerializer, melding)
        val resultJson = ResultJson(failure = fail.feilmelding.toJson())

        redisStore.set(RedisKey.of(clientId), resultJson.toJson(ResultJson.serializer()))
    }

    private fun lesStartdata(melding: Map<Key, JsonElement>): Array<Pair<Key, JsonElement>> {
        val orgnr = Key.ORGNRUNDERENHET.les(Orgnr.serializer(), melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)
        val innsenderFnr = Key.ARBEIDSGIVER_ID.les(Fnr.serializer(), melding)
        val sykmeldtFnr = Key.IDENTITETSNUMMER.les(Fnr.serializer(), melding)
        val skjema = Key.SKJEMA_INNTEKTSMELDING.les(Innsending.serializer(), melding)
        val clientId = Key.CLIENT_ID.les(UuidSerializer, melding)

        return listOf(
            Key.ORGNRUNDERENHET to orgnr.toJson(Orgnr.serializer()),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            Key.IDENTITETSNUMMER to sykmeldtFnr.toJson(Fnr.serializer()),
            Key.ARBEIDSGIVER_ID to innsenderFnr.toJson(Fnr.serializer()),
            Key.SKJEMA_INNTEKTSMELDING to skjema.toJson(Innsending.serializer()),
            Key.CLIENT_ID to clientId.toJson()
        ).toTypedArray()
    }

    private fun isOnStep0(melding: Map<Key, JsonElement>) = !isFinished(melding) && startKeys.all { it in melding }
}
