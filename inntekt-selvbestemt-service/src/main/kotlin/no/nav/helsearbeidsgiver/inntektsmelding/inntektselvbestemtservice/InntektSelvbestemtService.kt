package no.nav.helsearbeidsgiver.inntektsmelding.inntektselvbestemtservice

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.Tekst
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStoreClassSpecific
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.Service
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr

class InntektSelvbestemtService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStoreClassSpecific
) : Service() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override val eventName = EventName.INNTEKT_SELVBESTEMT_REQUESTED
    override val startKeys = setOf(
        Key.FNR,
        Key.ORGNRUNDERENHET,
        Key.SKJAERINGSTIDSPUNKT
    )
    override val dataKeys = setOf(
        Key.INNTEKT
    )

    override fun onStart(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val fnr = Key.FNR.les(Fnr.serializer(), melding)
        val orgnr = Key.ORGNRUNDERENHET.les(Orgnr.serializer(), melding)
        val inntektsdato = Key.SKJAERINGSTIDSPUNKT.les(LocalDateSerializer, melding)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId)
        ) {
            rapid.publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.INNTEKT.toJson(),
                Key.ORGNRUNDERENHET to orgnr.toJson(Orgnr.serializer()),
                Key.FNR to fnr.toJson(Fnr.serializer()),
                Key.SKJAERINGSTIDSPUNKT to inntektsdato.toJson(LocalDateSerializer),
                Key.UUID to transaksjonId.toJson()
            )
                .also {
                    MdcUtils.withLogFields(
                        Log.behov(BehovType.INNTEKT)
                    ) {
                        sikkerLogger.info("Publiserte melding:\n${it.toPretty()}.")
                    }
                }
        }
    }

    override fun onData(melding: Map<Key, JsonElement>) {
        if (isFinished(melding)) {
            val transaksjonId = Key.UUID.les(UuidSerializer, melding)
            val inntekt = Key.INNTEKT.les(Inntekt.serializer(), melding)

            val resultJson = ResultJson(
                success = inntekt.toJson(Inntekt.serializer())
            )
                .toJson(ResultJson.serializer())

            RedisKey.of(transaksjonId).write(resultJson)

            MdcUtils.withLogFields(
                Log.transaksjonId(transaksjonId)
            ) {
                sikkerLogger.info("$eventName fullført.")
            }
        } else {
            "Service skal aldri være \"underveis\".".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
        }
    }

    override fun onError(melding: Map<Key, JsonElement>, fail: Fail) {
        val feilmelding = Tekst.TEKNISK_FEIL_FORBIGAAENDE
        val resultJson = ResultJson(
            failure = feilmelding.toJson()
        )
            .toJson(ResultJson.serializer())

        "Returnerer feilmelding: '$feilmelding'".also {
            logger.error(it)
            sikkerLogger.error(it)
        }

        RedisKey.of(fail.transaksjonId).write(resultJson)

        MdcUtils.withLogFields(
            Log.transaksjonId(fail.transaksjonId)
        ) {
            sikkerLogger.error("$eventName terminert.")
        }
    }

    private fun RedisKey.write(json: JsonElement) {
        redisStore.set(this, json)
    }
}
