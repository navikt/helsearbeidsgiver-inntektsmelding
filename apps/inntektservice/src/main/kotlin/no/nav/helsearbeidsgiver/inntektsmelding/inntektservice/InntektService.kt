package no.nav.helsearbeidsgiver.inntektsmelding.inntektservice

import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.Tekst
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.inntektMapSerializer
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.Publisher
import no.nav.hag.simba.utils.rr.service.ServiceMed2Steg
import no.nav.hag.simba.utils.valkey.RedisStore
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
    private val publisher: Publisher,
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
        publisher
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
        publisher
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
