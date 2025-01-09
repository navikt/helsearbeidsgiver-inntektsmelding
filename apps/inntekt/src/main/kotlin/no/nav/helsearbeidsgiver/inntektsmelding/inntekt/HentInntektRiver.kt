package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Inntekt
import no.nav.helsearbeidsgiver.felles.domene.InntektPerMaaned
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.felles.utils.toYearMonth
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

data class Melding(
    val eventName: EventName,
    val behovType: BehovType,
    val transaksjonId: UUID,
    val data: Map<Key, JsonElement>,
    val svarKafkaKey: KafkaKey?,
    val orgnr: Orgnr,
    val fnr: Fnr,
    val inntektsdato: LocalDate,
)

class HentInntektRiver(
    private val inntektKlient: InntektKlient,
) : ObjectRiver<Melding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): Melding? =
        if (Key.FAIL in json) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()

            Melding(
                eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
                behovType = Key.BEHOV.krev(BehovType.HENT_INNTEKT, BehovType.serializer(), json),
                transaksjonId = Key.KONTEKST_ID.les(UuidSerializer, json),
                data = data,
                svarKafkaKey = Key.SVAR_KAFKA_KEY.lesOrNull(KafkaKey.serializer(), data),
                orgnr = Key.ORGNR_UNDERENHET.les(Orgnr.serializer(), data),
                fnr = Key.FNR.les(Fnr.serializer(), data),
                inntektsdato = Key.INNTEKTSDATO.les(LocalDateSerializer, data),
            )
        }

    override fun Melding.skrivNoekkel(): KafkaKey? = svarKafkaKey

    override fun Melding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        val fom = inntektsdato.minusMaaneder(3)
        val middle = inntektsdato.minusMaaneder(2)
        val tom = inntektsdato.minusMaaneder(1)

        val inntektPerOrgnrOgMaaned = hentInntektPerOrgnrOgMaaned(fnr, fom, tom, transaksjonId)

        val inntektPerMaaned = inntektPerOrgnrOgMaaned[orgnr.verdi].orEmpty()

        val inntekt =
            listOf(fom, middle, tom)
                .associateWith { inntektPerMaaned[it] }
                .map { (maaned, inntekt) -> InntektPerMaaned(maaned, inntekt) }
                .let(::Inntekt)

        return mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.KONTEKST_ID to transaksjonId.toJson(),
            Key.DATA to
                data
                    .plus(
                        Key.INNTEKT to inntekt.toJson(Inntekt.serializer()),
                    ).toJson(),
        )
    }

    override fun Melding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke hente inntekt fra Inntektskomponenten.",
                kontekstId = transaksjonId,
                utloesendeMelding = json,
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun Melding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@HentInntektRiver),
            Log.event(eventName),
            Log.behov(behovType),
            Log.transaksjonId(transaksjonId),
        )

    private fun hentInntektPerOrgnrOgMaaned(
        fnr: Fnr,
        fom: YearMonth,
        tom: YearMonth,
        transaksjonId: UUID,
    ): Map<String, Map<YearMonth, Double>> {
        val navConsumerId = "helsearbeidsgiver-im-inntekt"
        val callId = "$navConsumerId-$transaksjonId"

        sikkerLogger.info("Henter inntekt for $fnr i perioden $fom til $tom (callId: $callId).")

        return Metrics.inntektRequest.recordTime(inntektKlient::hentInntektPerOrgnrOgMaaned) {
            inntektKlient.hentInntektPerOrgnrOgMaaned(
                fnr = fnr.verdi,
                fom = fom,
                tom = tom,
                navConsumerId = navConsumerId,
                callId = callId,
            )
        }
    }
}

private fun LocalDate.minusMaaneder(maanederTilbake: Long): YearMonth = toYearMonth().minusMonths(maanederTilbake)
