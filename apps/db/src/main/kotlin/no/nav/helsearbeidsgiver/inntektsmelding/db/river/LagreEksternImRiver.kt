package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.kontrakt.domene.inntektsmelding.EksternInntektsmelding
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
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class LagreEksternImMelding(
    val eventName: EventName,
    val kontekstId: UUID,
    val forespoerselId: UUID,
    val eksternInntektsmelding: EksternInntektsmelding,
)

class LagreEksternImRiver(
    private val imRepo: InntektsmeldingRepository,
) : ObjectRiver.Simba<LagreEksternImMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): LagreEksternImMelding? =
        if (setOf(Key.BEHOV, Key.FAIL).any(json::containsKey)) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()

            LagreEksternImMelding(
                eventName = Key.EVENT_NAME.krev(EventName.EKSTERN_INNTEKTSMELDING_MOTTATT, EventName.serializer(), json),
                kontekstId = Key.KONTEKST_ID.les(UuidSerializer, json),
                forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, data),
                eksternInntektsmelding = Key.EKSTERN_INNTEKTSMELDING.les(EksternInntektsmelding.serializer(), data),
            )
        }

    override fun LagreEksternImMelding.bestemNoekkel(): KafkaKey = KafkaKey(forespoerselId)

    override fun LagreEksternImMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        imRepo.lagreEksternInntektsmelding(forespoerselId, eksternInntektsmelding)

        "Lagret ekstern inntektsmelding med arkiv referanse ${eksternInntektsmelding.arkivreferanse} i database.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        return mapOf(
            Key.EVENT_NAME to EventName.EKSTERN_INNTEKTSMELDING_LAGRET.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
        )
    }

    override fun LagreEksternImMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke lagre ekstern inntektsmelding i database.",
                kontekstId = kontekstId,
                utloesendeMelding = json,
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun LagreEksternImMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@LagreEksternImRiver),
            Log.event(eventName),
            Log.kontekstId(kontekstId),
            Log.forespoerselId(forespoerselId),
        )
}
