package no.nav.helsearbeidsgiver.felles.rapidsrivers.redis

import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName

sealed class RedisKey(open val uuid: String) {
    abstract override fun toString(): String

    companion object {
        fun of(uuid: String, dataFelt: DataFelt): RedisKey {
            return DataKey(uuid, dataFelt)
        }

        fun of(uuid: String): RedisKey {
            return ClientKey(uuid)
        }

        fun of(uuid: String, eventname: EventName): RedisKey {
            return TransactionKey(uuid, eventname)
        }
    }
}

private data class DataKey(override val uuid: String, val datafelt: DataFelt) : RedisKey(uuid) {
    override fun toString(): String {
        return uuid + datafelt.str
    }
}

private data class TransactionKey(override val uuid: String, val eventName: EventName) : RedisKey(uuid) {
    override fun toString(): String {
        return uuid + eventName.name
    }
}

private data class ClientKey(override val uuid: String) : RedisKey(uuid) {
    override fun toString(): String {
        return uuid
    }
}
