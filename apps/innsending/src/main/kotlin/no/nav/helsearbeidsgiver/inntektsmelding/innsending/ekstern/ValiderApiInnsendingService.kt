package no.nav.helsearbeidsgiver.inntektsmelding.innsending.ekstern

import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.json.inntektMapSerializer
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.utils.kontrakt.kafkatopic.innsending.Innsending.toJson
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.Publisher
import no.nav.hag.simba.utils.rr.service.ServiceMed2Steg
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.api.Innsending
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateTimeSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID
import no.nav.hag.simba.utils.kontrakt.kafkatopic.innsending.Innsending.EventName as InnsendingEventName
import no.nav.hag.simba.utils.kontrakt.kafkatopic.innsending.Innsending.Key as InnsendingKey

data class ValideringsSteg0(
    val kontekstId: UUID,
    val innsending: Innsending,
    val mottatt: LocalDateTime,
)

data class ValideringsSteg1(
    val forespoersel: Forespoersel,
)

data class ValideringsSteg2(
    val inntekt: Map<YearMonth, Double?>,
    val unnlatHentingAvInntekt: Boolean = false,
)

class ValiderApiInnsendingService(
    private val publisher: Publisher,
    private val producer: Producer,
) : ServiceMed2Steg<ValideringsSteg0, ValideringsSteg1, ValideringsSteg2>() {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.API_INNSENDING_STARTET

    override fun lesSteg0(melding: Map<Key, JsonElement>): ValideringsSteg0 =
        ValideringsSteg0(
            kontekstId = Key.KONTEKST_ID.les(UuidSerializer, melding),
            innsending = Key.INNSENDING.les(Innsending.serializer(), melding),
            mottatt = Key.MOTTATT.les(LocalDateTimeSerializer, melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): ValideringsSteg1 =
        ValideringsSteg1(
            forespoersel = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding),
        )

    override fun lesSteg2(melding: Map<Key, JsonElement>): ValideringsSteg2 =
        ValideringsSteg2(
            inntekt = Key.INNTEKT.les(inntektMapSerializer, melding),
        )

    override fun utfoerSteg0(
        data: Map<Key, JsonElement>,
        steg0: ValideringsSteg0,
    ) {
        publisher
            .publish(
                key = steg0.innsending.skjema.forespoerselId,
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                Key.DATA to
                    data
                        .plus(
                            Key.FORESPOERSEL_ID to
                                steg0.innsending.skjema.forespoerselId
                                    .toJson(),
                        ).toJson(),
            ).also { loggBehovPublisert(BehovType.HENT_TRENGER_IM, it) }
    }

    override fun utfoerSteg1(
        data: Map<Key, JsonElement>,
        steg0: ValideringsSteg0,
        steg1: ValideringsSteg1,
    ) {
        val inntekt = steg0.innsending.skjema.inntekt

        // Hvis inntektsmeldingen mangler inntekt eller har oppgitt endringsårsak, så er det ikke behov for å hente inntekt fra a-ordningen for validering
        if (inntekt == null || inntekt.endringAarsaker.isNotEmpty()) {
            utfoerSteg2(data, steg0, steg1, steg2 = ValideringsSteg2(inntekt = emptyMap(), unnlatHentingAvInntekt = true))
        } else {
            publisher
                .publish(
                    key = steg0.innsending.skjema.forespoerselId,
                    Key.EVENT_NAME to eventName.toJson(),
                    Key.BEHOV to BehovType.HENT_INNTEKT.toJson(),
                    Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                    Key.DATA to
                        data
                            .plus(
                                mapOf(
                                    Key.SVAR_KAFKA_KEY to KafkaKey(steg0.innsending.skjema.forespoerselId).toJson(),
                                    Key.ORGNR_UNDERENHET to steg1.forespoersel.orgnr.toJson(),
                                    Key.FNR to steg1.forespoersel.fnr.toJson(),
                                    Key.INNTEKTSDATO to inntekt.inntektsdato.toJson(),
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
    }

    override fun utfoerSteg2(
        data: Map<Key, JsonElement>,
        steg0: ValideringsSteg0,
        steg1: ValideringsSteg1,
        steg2: ValideringsSteg2,
    ) {
        val inntekt = steg0.innsending.skjema.inntekt

        val feilkoder =
            if (inntekt == null || steg2.unnlatHentingAvInntekt) {
                emptySet()
            } else {
                inntekt.validerInntektMotAordningen(aordningInntekt = steg2.inntekt)
            }

        if (feilkoder.isNotEmpty()) {
            val avvistInntektsmelding =
                AvvistInntektsmelding(
                    inntektsmeldingId = steg0.innsending.innsendingId,
                    feilkode = feilkoder.first(),
                )
            producer.send(
                key = steg0.innsending.skjema.forespoerselId,
                message =
                    mapOf(
                        InnsendingKey.EVENT_NAME to InnsendingEventName.AVVIST_INNTEKTSMELDING.toJson(),
                        InnsendingKey.KONTEKST_ID to steg0.kontekstId.toJson(),
                        InnsendingKey.DATA to
                            mapOf(
                                InnsendingKey.AVVIST_INNTEKTSMELDING to avvistInntektsmelding.toJson(AvvistInntektsmelding.serializer()),
                            ).toJson(),
                    ),
            )
            logger.info("Publiserte melding om avvist inntektsmelding med id ${avvistInntektsmelding.inntektsmeldingId}.")
        } else {
            val publisert =
                publisher.publish(
                    key = steg0.innsending.skjema.forespoerselId,
                    Key.EVENT_NAME to EventName.API_INNSENDING_VALIDERT.toJson(),
                    Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.MOTTATT to steg0.mottatt.toJson(),
                            Key.INNSENDING to steg0.innsending.toJson(Innsending.serializer()),
                            Key.FORESPOERSEL_SVAR to steg1.forespoersel.toJson(Forespoersel.serializer()),
                        ).toJson(),
                )

            MdcUtils.withLogFields(
                Log.event(EventName.API_INNSENDING_VALIDERT),
            ) {
                logger.info(
                    "Publiserte melding med event API_INNSENDING_VALIDERT for innsendingId ${steg0.innsending.innsendingId} " +
                        "og forespoerselId ${steg0.innsending.skjema.forespoerselId}.",
                )
                sikkerLogger.info("Publiserte melding:\n${publisert.toPretty()}")
            }
        }
    }

    override fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    ) {
        // FeilLytter plukker opp og retryer feil som inneholder eventet API_INNSENDING_STARTET
    }

    override fun ValideringsSteg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@ValiderApiInnsendingService),
            Log.event(eventName),
            Log.kontekstId(kontekstId),
            Log.inntektsmeldingId(innsending.innsendingId),
            Log.forespoerselId(innsending.skjema.forespoerselId),
        )

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
