package no.nav.helsearbeidsgiver.felles.rapidsrivers.redis

import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.utils.simpleName
import java.util.UUID

sealed class RedisKey {
    abstract val uuid: UUID
    abstract override fun toString(): String

    companion object {
        fun of(uuid: UUID): RedisKey =
            ClientKey(uuid)

        fun of(uuid: UUID, eventname: EventName): RedisKey =
            TransactionKey(uuid, eventname)

        fun of(uuid: UUID, key: Key): RedisKey =
            KeyKey(uuid, key)

        // @TODO ikke bra nok
        fun of(uuid: UUID, feilMelding: Feilmelding): RedisKey =
            FeilKey(uuid, feilMelding)
    }
}

private data class KeyKey(override val uuid: UUID, val key: Key) : RedisKey() {
    override fun toString(): String =
        uuid.toString() + key.toString()
}

private data class TransactionKey(override val uuid: UUID, val eventName: EventName) : RedisKey() {
    override fun toString(): String =
        uuid.toString() + eventName.name
}

private data class ClientKey(override val uuid: UUID) : RedisKey() {
    override fun toString(): String =
        uuid.toString()
}

private data class FeilKey(override val uuid: UUID, val feilMeldingClass: Feilmelding) : RedisKey() {
    override fun toString(): String =
        uuid.toString() + feilMeldingClass.simpleName()
}
