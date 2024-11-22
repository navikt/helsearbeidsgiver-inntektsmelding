package no.nav.helsearbeidsgiver.inntektsmelding.inntektselvbestemtservice

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Tekst
import no.nav.helsearbeidsgiver.felles.domene.Inntekt
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
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
    private val redisStore: RedisStore,
) : ServiceMed1Steg<Steg0, Steg1>() {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.INNTEKT_SELVBESTEMT_REQUESTED

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            transaksjonId = Key.KONTEKST_ID.les(UuidSerializer, melding),
            fnr = Key.FNR.les(Fnr.serializer(), melding),
            orgnr = Key.ORGNRUNDERENHET.les(Orgnr.serializer(), melding),
            inntektsdato = Key.INNTEKTSDATO.les(LocalDateSerializer, melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            inntekt = Key.INNTEKT.les(Inntekt.serializer(), melding),
        )

    override fun utfoerSteg0(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
    ) {
        val publisert =
            rapid.publish(
                key = steg0.fnr,
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_INNTEKT.toJson(),
                Key.KONTEKST_ID to steg0.transaksjonId.toJson(),
                Key.DATA to
                    data
                        .plus(
                            mapOf(
                                Key.ORGNRUNDERENHET to steg0.orgnr.toJson(),
                                Key.FNR to steg0.fnr.toJson(),
                                Key.INNTEKTSDATO to steg0.inntektsdato.toJson(),
                            ),
                        ).toJson(),
            )

        MdcUtils.withLogFields(
            Log.behov(BehovType.HENT_INNTEKT),
        ) {
            "Publiserte melding med behov ${BehovType.HENT_INNTEKT}.".let {
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
                success = steg1.inntekt.toJson(Inntekt.serializer()),
            )

        redisStore.skrivResultat(steg0.transaksjonId, resultJson)

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
            val resultJson = ResultJson(failure = feilmelding.toJson())

            "Returnerer feilmelding: '$feilmelding'".also {
                logger.error(it)
                sikkerLogger.error(it)
            }

            redisStore.skrivResultat(fail.transaksjonId, resultJson)

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
