package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toPretty
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class BesvartSimbaMelding(
    val eventName: EventName,
    val transaksjonId: UUID,
    val forespoerselId: UUID,
)

class ForespoerselBesvartFraSimbaRiver : ObjectRiver<BesvartSimbaMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): BesvartSimbaMelding? =
        if (setOf(Key.BEHOV, Key.DATA, Key.FAIL).any(json::containsKey)) {
            null
        } else {
            BesvartSimbaMelding(
                eventName = Key.EVENT_NAME.krev(EventName.INNTEKTSMELDING_MOTTATT, EventName.serializer(), json),
                transaksjonId = Key.UUID.les(UuidSerializer, json),
                forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, json),
            )
        }

    override fun BesvartSimbaMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        Metrics.forespoerslerBesvartFraSimba.inc()

        return notifikasjonHentIdMelding(transaksjonId, forespoerselId)
    }

    override fun BesvartSimbaMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement>? {
        "Klarte ikke umiddelbart markere forespørsel som besvart. Event fra helsebro skal løse dette.".also {
            logger.error(it)
            sikkerLogger.error("$it Aktuell melding:\n${json.toPretty()}")
        }

        return null
    }

    override fun BesvartSimbaMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@ForespoerselBesvartFraSimbaRiver),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId),
        )
}
