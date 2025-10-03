package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.json.krev
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.erDuplikatAv
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateTimeSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.time.LocalDateTime
import java.util.UUID

data class LagreImSkjemaMelding(
    val eventName: EventName,
    val behovType: BehovType,
    val kontekstId: UUID,
    val data: Map<Key, JsonElement>,
    val forespoersel: Forespoersel,
    val inntektsmeldingId: UUID,
    val skjema: SkjemaInntektsmelding,
    val avsenderFnr: Fnr,
    val mottatt: LocalDateTime,
)

class LagreImSkjemaRiver(
    private val repository: InntektsmeldingRepository,
) : ObjectRiver.Simba<LagreImSkjemaMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): LagreImSkjemaMelding? =
        if (Key.FAIL in json) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()
            LagreImSkjemaMelding(
                eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
                behovType = Key.BEHOV.krev(BehovType.LAGRE_IM_SKJEMA, BehovType.serializer(), json),
                kontekstId = Key.KONTEKST_ID.les(UuidSerializer, json),
                data = data,
                forespoersel = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), data),
                inntektsmeldingId = Key.INNTEKTSMELDING_ID.les(UuidSerializer, data),
                skjema = Key.SKJEMA_INNTEKTSMELDING.les(SkjemaInntektsmelding.serializer(), data),
                avsenderFnr = Key.ARBEIDSGIVER_FNR.les(Fnr.serializer(), data),
                mottatt = Key.MOTTATT.les(LocalDateTimeSerializer, data),
            )
        }

    override fun LagreImSkjemaMelding.bestemNoekkel(): KafkaKey = KafkaKey(skjema.forespoerselId)

    override fun LagreImSkjemaMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        val sisteImSkjema = repository.hentNyesteInntektsmeldingSkjema(skjema.forespoerselId)

        val erDuplikat = sisteImSkjema?.erDuplikatAv(skjema, forespoersel) ?: false

        if (erDuplikat) {
            sikkerLogger.warn("Fant duplikat av inntektsmeldingskjema.")
        } else {
            repository.lagreInntektsmeldingSkjema(inntektsmeldingId, skjema, avsenderFnr, mottatt)
            sikkerLogger.info("Lagret inntektsmeldingskjema.")
        }

        return mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                data
                    .plus(
                        Key.ER_DUPLIKAT_IM to erDuplikat.toJson(Boolean.serializer()),
                    ).toJson(),
        )
    }

    override fun LagreImSkjemaMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke lagre inntektsmeldingskjema i database.",
                kontekstId = kontekstId,
                utloesendeMelding = json,
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun LagreImSkjemaMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@LagreImSkjemaRiver),
            Log.event(eventName),
            Log.behov(behovType),
            Log.kontekstId(kontekstId),
            Log.inntektsmeldingId(inntektsmeldingId),
            Log.forespoerselId(skjema.forespoerselId),
        )
}
