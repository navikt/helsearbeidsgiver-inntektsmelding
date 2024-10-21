package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.db.ForespoerselRepository
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class LagreForespoerselMelding(
    val eventName: EventName,
    val behovType: BehovType,
    val transaksjonId: UUID,
    val forespoerselId: UUID,
    val orgnr: Orgnr,
)

class LagreForespoerselRiver(
    private val repository: ForespoerselRepository,
) : ObjectRiver<LagreForespoerselMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): LagreForespoerselMelding? =
        if (Key.FAIL in json) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()

            LagreForespoerselMelding(
                eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
                behovType = Key.BEHOV.krev(BehovType.LAGRE_FORESPOERSEL, BehovType.serializer(), json),
                transaksjonId = Key.UUID.les(UuidSerializer, json),
                forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, data),
                orgnr = Key.ORGNRUNDERENHET.les(Orgnr.serializer(), data),
            )
        }

    override fun LagreForespoerselMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement>? {
        repository.lagreForespoersel(forespoerselId.toString(), orgnr.verdi)
        return null
    }

    override fun LagreForespoerselMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement>? {
        "Klarte ikke lagre forespørsel.".also {
            logger.error(it)
            sikkerLogger.error(it, error)
        }
        return null
    }

    override fun LagreForespoerselMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@LagreForespoerselRiver),
            Log.event(eventName),
            Log.behov(behovType),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId),
        )
}
