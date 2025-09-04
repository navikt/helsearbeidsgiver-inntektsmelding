package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.inntektMapSerializer
import no.nav.hag.simba.utils.felles.json.krev
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.model.Fail
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.felles.utils.toYearMonth
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.river.ObjectRiver
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
    val kontekstId: UUID,
    val data: Map<Key, JsonElement>,
    val svarKafkaKey: KafkaKey,
    val orgnr: Orgnr,
    val fnr: Fnr,
    val inntektsdato: LocalDate,
)

class HentInntektRiver(
    private val inntektKlient: InntektKlient,
) : ObjectRiver.Simba<Melding>() {
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
                kontekstId = Key.KONTEKST_ID.les(UuidSerializer, json),
                data = data,
                svarKafkaKey = Key.SVAR_KAFKA_KEY.les(KafkaKey.serializer(), data),
                orgnr = Key.ORGNR_UNDERENHET.les(Orgnr.serializer(), data),
                fnr = Key.FNR.les(Fnr.serializer(), data),
                inntektsdato = Key.INNTEKTSDATO.les(LocalDateSerializer, data),
            )
        }

    override fun Melding.bestemNoekkel(): KafkaKey = svarKafkaKey

    override fun Melding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        val fom = inntektsdato.minusMaaneder(3)
        val middle = inntektsdato.minusMaaneder(2)
        val tom = inntektsdato.minusMaaneder(1)

        val inntektPerOrgnrOgMaaned = hentInntektPerOrgnrOgMaaned(fnr, fom, tom, kontekstId)

        val inntektPerMaaned = inntektPerOrgnrOgMaaned[orgnr.verdi].orEmpty()

        val inntekt =
            listOf(fom, middle, tom)
                .associateWith { inntektPerMaaned[it] }

        return mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                data
                    .plus(
                        Key.INNTEKT to inntekt.toJson(inntektMapSerializer),
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
                kontekstId = kontekstId,
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
            Log.kontekstId(kontekstId),
        )

    private fun hentInntektPerOrgnrOgMaaned(
        fnr: Fnr,
        fom: YearMonth,
        tom: YearMonth,
        kontekstId: UUID,
    ): Map<String, Map<YearMonth, Double>> {
        val navConsumerId = "helsearbeidsgiver-im-inntekt"
        val callId = "$navConsumerId-$kontekstId"

        sikkerLogger.info("Henter inntekt for $fnr i perioden $fom til $tom (callId: $callId).")

        return runBlocking {
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
