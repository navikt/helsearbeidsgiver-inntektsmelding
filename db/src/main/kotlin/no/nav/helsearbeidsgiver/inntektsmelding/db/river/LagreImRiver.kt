package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
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

data class LagreImMelding(
    val eventName: EventName,
    val behovType: BehovType,
    val transaksjonId: UUID,
    val data: Map<Key, JsonElement>,
    val forespoerselId: UUID,
    val inntektsmelding: Inntektsmelding,
)

class LagreImRiver(
    private val imRepo: InntektsmeldingRepository,
) : ObjectRiver<LagreImMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): LagreImMelding? =
        if (Key.FAIL in json) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()

            LagreImMelding(
                eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
                behovType = Key.BEHOV.krev(BehovType.LAGRE_IM, BehovType.serializer(), json),
                transaksjonId = Key.UUID.les(UuidSerializer, json),
                data = data,
                forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, data),
                inntektsmelding = Key.INNTEKTSMELDING.les(Inntektsmelding.serializer(), data),
            )
        }

    override fun LagreImMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        val nyesteIm = imRepo.hentNyesteInntektsmelding(forespoerselId)
        val nyesteImSkjema = imRepo.hentNyesteInntektsmeldingSkjema(forespoerselId)

        val erDuplikatAvNyesteIm = nyesteIm?.erDuplikatAv(inntektsmelding) ?: false

        // Dersom det finnes et nyere skjema med samme forespørsel-ID som *ikke* er lik inntektsmeldingen vi forsøker å lagre,
        // så har det blitt sendt inn en nyere utgave av denne inntektsmeldingen, og den skal derfor ikke lagres.
        val erDuplikatAvNyesteImSkjema = nyesteImSkjema?.let { inntektsmelding.erDuplikatAv(it) }
        val erUtdatertIm = erDuplikatAvNyesteImSkjema == false

        if (erDuplikatAvNyesteIm) {
            sikkerLogger.warn("Fant duplikat av inntektsmelding.")
        } else if (erUtdatertIm) {
            sikkerLogger.warn("Fant en nyere utgave av inntektsmelding.")
        } else {
            imRepo.oppdaterInntektsmeldingMedDokument(forespoerselId, inntektsmelding)
            sikkerLogger.info("Lagret inntektsmelding.")
        }

        return mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                data
                    .plus(
                        mapOf(
                            Key.ER_DUPLIKAT_IM to erDuplikatAvNyesteIm.toJson(Boolean.serializer()),
                            Key.ER_UTDATERT_IM to erUtdatertIm.toJson(Boolean.serializer()),
                        ),
                    ).toJson(),
        )
    }

    override fun LagreImMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke lagre inntektsmelding i database.",
                event = eventName,
                transaksjonId = transaksjonId,
                forespoerselId = forespoerselId,
                utloesendeMelding = json.toJson(),
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun LagreImMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@LagreImRiver),
            Log.event(eventName),
            Log.behov(behovType),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId),
        )
}
