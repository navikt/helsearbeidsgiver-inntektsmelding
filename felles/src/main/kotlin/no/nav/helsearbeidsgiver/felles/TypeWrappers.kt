package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import java.util.UUID

@JvmInline
value class RedisKey(val verdi: String) {
    constructor(id: UUID) : this(id.toString())
}

@Serializable
@JvmInline
value class Identitetsnummer(val verdi: String)
