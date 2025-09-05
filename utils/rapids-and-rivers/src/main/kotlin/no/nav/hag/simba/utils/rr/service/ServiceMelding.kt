package no.nav.hag.simba.utils.rr.service

import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
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
