package no.nav.helsearbeidsgiver.felles.rapidsrivers.service

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore

abstract class Service {
    abstract val redisStore: RedisStore
    abstract val eventName: EventName
    abstract val startKeys: Set<Key>
    abstract val dataKeys: Set<Key>

    abstract fun onStart(melding: Map<Key, JsonElement>)
    abstract fun onData(melding: Map<Key, JsonElement>)
    abstract fun onError(melding: Map<Key, JsonElement>, fail: Fail)

    fun isFinished(melding: Map<Key, JsonElement>): Boolean =
        dataKeys.all(melding::containsKey)

    internal fun isInactive(redisData: Map<Key, JsonElement>): Boolean =
        !startKeys.all(redisData::containsKey)
}
