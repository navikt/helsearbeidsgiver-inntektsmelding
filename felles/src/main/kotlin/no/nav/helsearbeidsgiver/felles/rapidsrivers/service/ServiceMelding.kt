package no.nav.helsearbeidsgiver.felles.rapidsrivers.service

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import java.util.UUID

sealed class ServiceMelding

data class StartMelding(
    val eventName: EventName,
    val clientId: UUID?,
    val transaksjonId: UUID,
    val startDataMap: Map<Key, JsonElement>
) : ServiceMelding()

data class DataMelding(
    val eventName: EventName,
    val transaksjonId: UUID,
    val dataMap: Map<Key, JsonElement>
) : ServiceMelding()

data class FailMelding(
    val eventName: EventName,
    val transaksjonId: UUID,
    val fail: Fail
) : ServiceMelding()
