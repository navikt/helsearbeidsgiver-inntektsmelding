package no.nav.helsearbeidsgiver.inntektsmelding.inntektselvbestemtservice

import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.Tekst
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.inntektMapSerializer
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.model.Fail
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.Publisher
import no.nav.hag.simba.utils.rr.service.ServiceMed1Steg
import no.nav.hag.simba.utils.valkey.RedisStore
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
import java.time.YearMonth
import java.util.UUID

data class Steg0(
    val kontekstId: UUID,
    val orgnr: Orgnr,
    val sykmeldtFnr: Fnr,
    val inntektsdato: LocalDate,
)

data class Steg1(
    val inntekt: Map<YearMonth, Double?>,
)

class InntektSelvbestemtService(
    private val publisher: Publisher,
    private val redisStore: RedisStore,
) : ServiceMed1Steg<Steg0, Steg1>() {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.INNTEKT_SELVBESTEMT_REQUESTED

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            kontekstId = Key.KONTEKST_ID.les(UuidSerializer, melding),
            orgnr = Key.ORGNR_UNDERENHET.les(Orgnr.serializer(), melding),
            sykmeldtFnr = Key.FNR.les(Fnr.serializer(), melding),
            inntektsdato = Key.INNTEKTSDATO.les(LocalDateSerializer, melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            inntekt = Key.INNTEKT.les(inntektMapSerializer, melding),
        )

    override fun utfoerSteg0(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
    ) {
        val publisert =
            publisher.publish(
                key = steg0.sykmeldtFnr,
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_INNTEKT.toJson(),
                Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                Key.DATA to
                    data
                        .plus(
                            mapOf(
                                Key.SVAR_KAFKA_KEY to KafkaKey(steg0.sykmeldtFnr).toJson(),
                                Key.ORGNR_UNDERENHET to steg0.orgnr.toJson(),
                                Key.FNR to steg0.sykmeldtFnr.toJson(),
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
                success = steg1.inntekt.toJson(inntektMapSerializer),
            )

        redisStore.skrivResultat(steg0.kontekstId, resultJson)

        sikkerLogger.info("$eventName fullført.")
    }

    override fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    ) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(eventName),
            Log.kontekstId(fail.kontekstId),
        ) {
            val feilmelding = Tekst.TEKNISK_FEIL_FORBIGAAENDE
            val resultJson = ResultJson(failure = feilmelding.toJson())

            "Returnerer feilmelding: '$feilmelding'".also {
                logger.error(it)
                sikkerLogger.error(it)
            }

            redisStore.skrivResultat(fail.kontekstId, resultJson)

            sikkerLogger.error("$eventName terminert.")
        }
    }

    override fun Steg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@InntektSelvbestemtService),
            Log.event(eventName),
            Log.kontekstId(kontekstId),
        )
}
