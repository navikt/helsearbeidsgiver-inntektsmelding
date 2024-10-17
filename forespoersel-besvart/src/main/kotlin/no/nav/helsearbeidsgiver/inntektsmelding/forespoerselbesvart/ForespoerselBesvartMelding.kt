package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

fun forespoerselBesvartMelding(
    transaksjonId: UUID,
    forespoerselId: UUID,
): Map<Key, JsonElement> =
    mapOf(
        Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
        Key.UUID to transaksjonId.toJson(),
        Key.FORESPOERSEL_ID to forespoerselId.toJson(),
    )
