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
    val innsendingId: Long,
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
                innsendingId = Key.INNSENDING_ID.les(Long.serializer(), data),
            )
        }

    override fun LagreImMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        val nyesteIm = imRepo.hentNyesteInntektsmelding(forespoerselId)

        // TODO: Vi tror at denne duplikatsjekken kan fjernes helt etter at vi har gått i prod med den nye Innsending-flyten
        val erDuplikat = nyesteIm?.erDuplikatAv(inntektsmelding) ?: false

        if (erDuplikat) {
            sikkerLogger.warn("Fant duplikat av inntektsmelding.")
        } else {
            imRepo.oppdaterMedBeriketDokument(forespoerselId, innsendingId, inntektsmelding)
            sikkerLogger.info("Lagret inntektsmelding.")
        }

        return mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                data
                    .plus(
                        mapOf(
                            Key.ER_DUPLIKAT_IM to erDuplikat.toJson(Boolean.serializer()),
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
