package no.nav.helsearbeidsgiver.felles.rapidsrivers.service

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import java.util.UUID

sealed class ServiceMelding

data class DataMelding(
    val eventName: EventName,
    val kontekstId: UUID,
    val dataMap: Map<Key, JsonElement>,
) : ServiceMelding()

data class FailMelding(
    val eventName: EventName,
    val kontekstId: UUID,
    val fail: Fail,
) : ServiceMelding()
