package no.nav.helsearbeidsgiver.inntektsmelding.inntektservice

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Tekst
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.inntektMapSerializer
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceMed2Steg
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

data class Steg0(
    val kontekstId: UUID,
    val forespoerselId: UUID,
    val inntektsdato: LocalDate,
)

data class Steg1(
    val forespoersel: Forespoersel,
)

data class Steg2(
    val inntekt: Map<YearMonth, Double?>,
)

class InntektService(
    private val rapid: RapidsConnection,
    private val redisStore: RedisStore,
) : ServiceMed2Steg<Steg0, Steg1, Steg2>() {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.INNTEKT_REQUESTED

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            kontekstId = Key.KONTEKST_ID.les(UuidSerializer, melding),
            forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding),
            inntektsdato = Key.INNTEKTSDATO.les(LocalDateSerializer, melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            forespoersel = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding),
        )

    override fun lesSteg2(melding: Map<Key, JsonElement>): Steg2 =
        Steg2(
            inntekt = Key.INNTEKT.les(inntektMapSerializer, melding),
        )

    override fun utfoerSteg0(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
    ) {
        rapid
            .publish(
                key = steg0.forespoerselId,
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                Key.DATA to
                    data
                        .plus(
                            Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                        ).toJson(),
            ).also {
                MdcUtils.withLogFields(
                    Log.behov(BehovType.HENT_TRENGER_IM),
                ) {
                    sikkerLogger.info("Publiserte melding:\n${it.toPretty()}")
                }
            }
    }

    override fun utfoerSteg1(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
    ) {
        rapid
            .publish(
                key = steg0.forespoerselId,
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_INNTEKT.toJson(),
                Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                Key.DATA to
                    data
                        .plus(
                            mapOf(
                                Key.SVAR_KAFKA_KEY to KafkaKey(steg0.forespoerselId).toJson(),
                                Key.ORGNR_UNDERENHET to steg1.forespoersel.orgnr.toJson(),
                                Key.FNR to steg1.forespoersel.fnr.toJson(),
                                Key.INNTEKTSDATO to steg0.inntektsdato.toJson(),
                            ),
                        ).toJson(),
            ).also {
                MdcUtils.withLogFields(
                    Log.behov(BehovType.HENT_INNTEKT),
                ) {
                    sikkerLogger.info("Publiserte melding:\n${it.toPretty()}")
                }
            }
    }

    override fun utfoerSteg2(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
    ) {
        val resultJson =
            ResultJson(
                success = steg2.inntekt.toJson(inntektMapSerializer),
            )

        redisStore.skrivResultat(steg0.kontekstId, resultJson)

        sikkerLogger.info("$eventName fullført.")
    }

    override fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    ) {
        val feilmelding = Tekst.TEKNISK_FEIL_FORBIGAAENDE
        val resultJson = ResultJson(failure = feilmelding.toJson())

        "Returnerer feilmelding: '$feilmelding'".also {
            logger.error(it)
            sikkerLogger.error(it)
        }

        redisStore.skrivResultat(fail.kontekstId, resultJson)

        MdcUtils.withLogFields(
            Log.kontekstId(fail.kontekstId),
        ) {
            sikkerLogger.error("$eventName terminert.")
        }
    }

    override fun Steg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@InntektService),
            Log.event(eventName),
            Log.kontekstId(kontekstId),
            Log.forespoerselId(forespoerselId),
        )
}
