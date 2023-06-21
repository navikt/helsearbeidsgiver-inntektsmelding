package no.nav.helsearbeidsgiver.felles.rapidsrivers.redis

import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Feilmelding

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

        // @TODO ikke bra nok
        fun of(uuid: String, feilMelding: Feilmelding): RedisKey {
            return FeilKey(uuid, feilMelding)
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

private data class FeilKey(override val uuid: String, val feilMeldingClass: Feilmelding) : RedisKey(uuid) {
    override fun toString(): String {
        return uuid + feilMeldingClass.javaClass.simpleName
    }
}
