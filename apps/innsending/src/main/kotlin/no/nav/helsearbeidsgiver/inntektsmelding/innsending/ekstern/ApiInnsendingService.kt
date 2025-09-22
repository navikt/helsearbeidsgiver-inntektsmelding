package no.nav.helsearbeidsgiver.inntektsmelding.innsending.ekstern

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.utils.rr.Publisher
import no.nav.hag.simba.utils.rr.service.ServiceMed1Steg
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.api.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateTimeSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.time.LocalDateTime
import java.util.UUID

data class Steg0(
    val kontekstId: UUID,
    val innsending: Innsending,
    val mottatt: LocalDateTime,
    val forespoersel: Forespoersel,
)

data class Steg1(
    val inntektsmeldingId: UUID,
    val erDuplikat: Boolean,
)

class ApiInnsendingService(
    private val publisher: Publisher,
) : ServiceMed1Steg<Steg0, Steg1>() {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.API_INNSENDING_VALIDERT

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            kontekstId = Key.KONTEKST_ID.les(UuidSerializer, melding),
            innsending = Key.INNSENDING.les(Innsending.serializer(), melding),
            mottatt = Key.MOTTATT.les(LocalDateTimeSerializer, melding),
            forespoersel = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            inntektsmeldingId = Key.INNTEKTSMELDING_ID.les(UuidSerializer, melding),
            erDuplikat = Key.ER_DUPLIKAT_IM.les(Boolean.serializer(), melding),
        )

    override fun utfoerSteg0(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
    ) {
        publisher
            .publish(
                key = steg0.innsending.skjema.forespoerselId,
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.LAGRE_IM_SKJEMA.toJson(),
                Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                Key.DATA to
                    data
                        .plus(
                            mapOf(
                                Key.INNTEKTSMELDING_ID to steg0.innsending.innsendingId.toJson(),
                                Key.SKJEMA_INNTEKTSMELDING to steg0.innsending.skjema.toJson(SkjemaInntektsmelding.serializer()),
                            ),
                        ).toJson(),
            ).also { loggBehovPublisert(BehovType.LAGRE_IM_SKJEMA, it) }
    }

    override fun utfoerSteg1(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
    ) {
        if (!steg1.erDuplikat) {
            val publisert =
                publisher.publish(
                    key = steg0.innsending.skjema.forespoerselId,
                    Key.EVENT_NAME to EventName.INNTEKTSMELDING_SKJEMA_LAGRET.toJson(),
                    Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FORESPOERSEL_SVAR to steg0.forespoersel.toJson(Forespoersel.serializer()),
                            Key.INNTEKTSMELDING_ID to steg1.inntektsmeldingId.toJson(),
                            Key.SKJEMA_INNTEKTSMELDING to steg0.innsending.skjema.toJson(SkjemaInntektsmelding.serializer()), // fjern
                            Key.MOTTATT to steg0.mottatt.toJson(),
                            Key.INNSENDING to steg0.innsending.toJson(Innsending.serializer()),
                        ).toJson(),
                )

            MdcUtils.withLogFields(
                Log.event(EventName.INNTEKTSMELDING_SKJEMA_LAGRET),
            ) {
                logger.info("Publiserte melding.")
                sikkerLogger.info("Publiserte melding:\n${publisert.toPretty()}")
            }
        }
    }

    override fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    ) {
        // FeilLytter plukker opp og retryer feil som inneholder eventet API_INNSENDING_VALIDERT
    }

    override fun Steg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@ApiInnsendingService),
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
