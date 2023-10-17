package no.nav.helsearbeidsgiver.felles.rapidsrivers.model

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helsearbeidsgiver.felles.IKey

interface Message {

    operator fun get(key: IKey): JsonNode

    operator fun set(key: IKey, value: Any)
    fun toJsonMessage(): JsonMessage
}
