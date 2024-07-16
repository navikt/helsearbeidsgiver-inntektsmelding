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
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceMed1Steg
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
import java.time.LocalDate
import java.util.UUID

data class Steg0(
    val transaksjonId: UUID,
    val orgnr: Orgnr,
    val fnr: Fnr,
    val inntektsdato: LocalDate,
)

data class Steg1(
    val inntekt: Inntekt,
)

class InntektSelvbestemtService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStoreClassSpecific,
) : ServiceMed1Steg<Steg0, Steg1>() {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

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

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            transaksjonId = Key.UUID.les(UuidSerializer, melding),
            fnr = Key.FNR.les(Fnr.serializer(), melding),
            orgnr = Key.ORGNRUNDERENHET.les(Orgnr.serializer(), melding),
            inntektsdato = Key.SKJAERINGSTIDSPUNKT.les(LocalDateSerializer, melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            inntekt = Key.INNTEKT.les(Inntekt.serializer(), melding),
        )

    override fun utfoerSteg0(steg0: Steg0) {
        val publisert =
            rapid.publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.INNTEKT.toJson(),
                Key.UUID to steg0.transaksjonId.toJson(),
                Key.FNR to steg0.fnr.toJson(),
                Key.ORGNRUNDERENHET to steg0.orgnr.toJson(),
                Key.SKJAERINGSTIDSPUNKT to steg0.inntektsdato.toJson(LocalDateSerializer),
            )

        MdcUtils.withLogFields(
            Log.behov(BehovType.INNTEKT),
        ) {
            "Publiserte melding med behov ${BehovType.INNTEKT}.".let {
                logger.info(it)
                sikkerLogger.info("$it\n${publisert.toPretty()}")
            }
        }
    }

    override fun utfoerSteg1(
        steg0: Steg0,
        steg1: Steg1,
    ) {
        val resultJson =
            ResultJson(
                success = steg1.inntekt.toJson(Inntekt.serializer()),
            ).toJson(ResultJson.serializer())

        redisStore.set(RedisKey.of(steg0.transaksjonId), resultJson)

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
            val feilmelding = Tekst.TEKNISK_FEIL_FORBIGAAENDE
            val resultJson =
                ResultJson(
                    failure = feilmelding.toJson(),
                ).toJson(ResultJson.serializer())

            "Returnerer feilmelding: '$feilmelding'".also {
                logger.error(it)
                sikkerLogger.error(it)
            }

            redisStore.set(RedisKey.of(fail.transaksjonId), resultJson)

            sikkerLogger.error("$eventName terminert.")
        }
    }

    override fun Steg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@InntektSelvbestemtService),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
        )
}
