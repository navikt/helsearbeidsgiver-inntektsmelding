package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Innsending
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.erDuplikatAv
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class PersisterImSkjemaMelding(
    val eventName: EventName,
    val behovType: BehovType,
    val transaksjonId: UUID,
    val data: Map<Key, JsonElement>,
    val forespoerselId: UUID,
    val inntektsmeldingSkjema: Innsending,
)

class PersisterImSkjemaRiver(
    private val repository: InntektsmeldingRepository,
) : ObjectRiver<PersisterImSkjemaMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): PersisterImSkjemaMelding? =
        if (Key.FAIL in json) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()
            PersisterImSkjemaMelding(
                eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
                behovType = Key.BEHOV.krev(BehovType.PERSISTER_IM_SKJEMA, BehovType.serializer(), json),
                transaksjonId = Key.UUID.les(UuidSerializer, json),
                data = data,
                forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, data),
                inntektsmeldingSkjema = Key.SKJEMA_INNTEKTSMELDING.les(Innsending.serializer(), data),
            )
        }

    override fun PersisterImSkjemaMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        val sisteIm = repository.hentNyesteInntektsmeldingSkjema(forespoerselId)
        val sisteImSkjema = repository.hentNyesteInntektsmeldingSkjema(forespoerselId)

        val erDuplikat =
            sisteIm?.erDuplikatAv(inntektsmeldingSkjema) ?: false ||
                sisteImSkjema?.erDuplikatAv(inntektsmeldingSkjema) ?: false

        if (erDuplikat) {
            sikkerLogger.warn("Fant duplikat av inntektsmelding for forespoerselId: $forespoerselId")
        } else {
            repository.lagreInntektsmeldingSkjema(forespoerselId.toString(), inntektsmeldingSkjema)
            sikkerLogger.info("Lagret inntektsmeldingskjema for forespoerselId: $forespoerselId")
        }
        return mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                data
                    .plus(
                        Key.ER_DUPLIKAT_IM to erDuplikat.toJson(Boolean.serializer()),
                    ).toJson(),
        )
    }

    override fun PersisterImSkjemaMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke lagre inntektsmeldingskjema.",
                event = eventName,
                transaksjonId = transaksjonId,
                forespoerselId = forespoerselId,
                utloesendeMelding = json.toJson(),
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun PersisterImSkjemaMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@PersisterImSkjemaRiver),
            Log.event(eventName),
            Log.behov(behovType),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId),
        )
}
