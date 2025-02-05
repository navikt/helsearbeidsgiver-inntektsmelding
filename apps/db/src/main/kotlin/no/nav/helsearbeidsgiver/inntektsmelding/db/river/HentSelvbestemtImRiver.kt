package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.db.SelvbestemtImRepo
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class HentSelvbestemtImMelding(
    val eventName: EventName,
    val behovType: BehovType,
    val kontekstId: UUID,
    val data: Map<Key, JsonElement>,
    val selvbestemtId: UUID,
)

class HentSelvbestemtImRiver(
    private val selvbestemtImRepo: SelvbestemtImRepo,
) : ObjectRiver<HentSelvbestemtImMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): HentSelvbestemtImMelding? =
        if (Key.FAIL in json) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()

            HentSelvbestemtImMelding(
                eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
                behovType = Key.BEHOV.krev(BehovType.HENT_SELVBESTEMT_IM, BehovType.serializer(), json),
                kontekstId = Key.KONTEKST_ID.les(UuidSerializer, json),
                data = data,
                selvbestemtId = Key.SELVBESTEMT_ID.les(UuidSerializer, data),
            )
        }

    override fun HentSelvbestemtImMelding.bestemNoekkel(): KafkaKey = KafkaKey(selvbestemtId)

    override fun HentSelvbestemtImMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        "Skal hente selvbestemt inntektsmelding.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        val inntektsmelding = selvbestemtImRepo.hentNyesteIm(selvbestemtId)

        return if (inntektsmelding != null) {
            "Hentet selvbestemt inntektsmelding.".also {
                logger.info(it)
                sikkerLogger.info(it)
            }

            mapOf(
                Key.EVENT_NAME to eventName.toJson(),
                Key.KONTEKST_ID to kontekstId.toJson(),
                Key.DATA to
                    data
                        .plus(
                            Key.SELVBESTEMT_INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
                        ).toJson(),
            )
        } else {
            haandterFeil("Fant ikke selvbestemt inntektsmelding.", json)
        }
    }

    override fun HentSelvbestemtImMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> = haandterFeil("Klarte ikke hente selvbestemt inntektsmelding.", json, error)

    override fun HentSelvbestemtImMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@HentSelvbestemtImRiver),
            Log.event(eventName),
            Log.behov(behovType),
            Log.kontekstId(kontekstId),
            Log.selvbestemtId(selvbestemtId),
        )

    private fun HentSelvbestemtImMelding.haandterFeil(
        feilmelding: String,
        json: Map<Key, JsonElement>,
        error: Throwable? = null,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = feilmelding,
                kontekstId = kontekstId,
                utloesendeMelding = json,
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }
}
