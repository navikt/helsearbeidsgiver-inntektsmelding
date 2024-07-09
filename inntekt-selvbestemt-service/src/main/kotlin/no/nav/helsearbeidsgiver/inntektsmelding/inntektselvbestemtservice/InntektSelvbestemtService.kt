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
    override val redisStore: RedisStoreClassSpecific,
) : Service() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override val eventName = EventName.INNTEKT_SELVBESTEMT_REQUESTED
    override val startKeys =
        setOf(
            Key.FNR,
            Key.ORGNRUNDERENHET,
            Key.SKJAERINGSTIDSPUNKT,
        )
    override val dataKeys =
        setOf(
            Key.INNTEKT,
        )

    override fun onData(melding: Map<Key, JsonElement>) {
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)

        if (isFinished(melding)) {
            val inntekt = Key.INNTEKT.les(Inntekt.serializer(), melding)

            val resultJson =
                ResultJson(
                    success = inntekt.toJson(Inntekt.serializer()),
                )
                    .toJson(ResultJson.serializer())

            RedisKey.of(transaksjonId).write(resultJson)

            sikkerLogger.info("$eventName fullført.")
        } else {
            val fnr = Key.FNR.les(Fnr.serializer(), melding)
            val orgnr = Key.ORGNRUNDERENHET.les(Orgnr.serializer(), melding)
            val inntektsdato = Key.SKJAERINGSTIDSPUNKT.les(LocalDateSerializer, melding)

            rapid.publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.INNTEKT.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FNR to fnr.toJson(),
                Key.ORGNRUNDERENHET to orgnr.toJson(),
                Key.SKJAERINGSTIDSPUNKT to inntektsdato.toJson(LocalDateSerializer),
            )
                .also { loggBehovPublisert(BehovType.INNTEKT, it) }
        }
    }

    override fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    ) {
        val feilmelding = Tekst.TEKNISK_FEIL_FORBIGAAENDE
        val resultJson =
            ResultJson(
                failure = feilmelding.toJson(),
            )
                .toJson(ResultJson.serializer())

        "Returnerer feilmelding: '$feilmelding'".also {
            logger.error(it)
            sikkerLogger.error(it)
        }

        RedisKey.of(fail.transaksjonId).write(resultJson)

        MdcUtils.withLogFields(
            Log.transaksjonId(fail.transaksjonId),
        ) {
            sikkerLogger.error("$eventName terminert.")
        }
    }

    private fun RedisKey.write(json: JsonElement) {
        redisStore.set(this, json)
    }

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
