package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
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

data class LagreImSkjemaMelding(
    val eventName: EventName,
    val behovType: BehovType,
    val transaksjonId: UUID,
    val data: Map<Key, JsonElement>,
    val forespoerselId: UUID,
    val inntektsmeldingSkjema: SkjemaInntektsmelding,
)

class LagreImSkjemaRiver(
    private val repository: InntektsmeldingRepository,
) : ObjectRiver<LagreImSkjemaMelding>() {
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
                transaksjonId = Key.UUID.les(UuidSerializer, json),
                data = data,
                forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, data),
                inntektsmeldingSkjema = Key.SKJEMA_INNTEKTSMELDING.les(SkjemaInntektsmelding.serializer(), data),
            )
        }

    override fun LagreImSkjemaMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        val sisteImSkjema = repository.hentNyesteInntektsmeldingSkjema(forespoerselId)

        val erDuplikat = sisteImSkjema?.erDuplikatAv(inntektsmeldingSkjema) ?: false

        val innsendingId =
            if (erDuplikat) {
                sikkerLogger.warn("Fant duplikat av inntektsmeldingskjema.")
                -1 // TODO: Hva skal vi sette denne til når vi får et duplikat skjema inn?
            } else {
                repository.lagreInntektsmeldingSkjema(forespoerselId, inntektsmeldingSkjema).also {
                    sikkerLogger.info("Lagret inntektsmeldingskjema.")
                }
            }
        return mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                data
                    .plus(
                        mapOf(
                            Key.ER_DUPLIKAT_IM to erDuplikat.toJson(Boolean.serializer()),
                            Key.INNSENDING_ID to innsendingId.toJson(Long.serializer()),
                        ),
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
                event = eventName,
                transaksjonId = transaksjonId,
                forespoerselId = forespoerselId,
                utloesendeMelding = json.toJson(),
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
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId),
        )
}
