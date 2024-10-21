package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class FjernPaaminnelseMelding(
    val eventName: EventName,
    val transaksjonId: UUID,
    val forespoerselId: UUID,
)

class FjernPaaminnelseRiver : ObjectRiver<FjernPaaminnelseMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun FjernPaaminnelseMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement>? {
        "FjernPaaminnelseRiver skulle ha fjernet påminnelsen på oppgaven, men det er ikke implementert ennå.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }
        return null
    }

    override fun les(json: Map<Key, JsonElement>): FjernPaaminnelseMelding? =
        if (Key.FAIL in json) {
            null
        } else {
            FjernPaaminnelseMelding(
                eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
                transaksjonId = Key.UUID.les(UuidSerializer, json),
                forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, json),
            )
        }

    override fun FjernPaaminnelseMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@FjernPaaminnelseRiver),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId),
        )

    override fun FjernPaaminnelseMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement>? {
        // Sjekk error før vi logger
        // Vi forventer at mange oppgaver ikke er opprettet enda pga begrensede forespørsler
        // disse vil trolig feile med SakEllerOppgaveFinnesIkkeException
        TODO("Not yet implemented")
    }
}
