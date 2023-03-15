package no.nav.helsearbeidsgiver.felles.message

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.json.fromJson

// TODO rename? inneholder både løsninger og input fra api-behov
@Serializable
data class Løsninger(
    private val verdi: Map<String, JsonElement> // TODO hvordan serialiseres denne?
) {
    fun løsteBehov(): Set<BehovType> =
        verdi.keys.mapNotNull {
            runCatching { BehovType.valueOf(it) }.getOrNull()
        }.toSet()

    fun plus(keyValuePair: Pair<String, JsonElement>): Løsninger =
        verdi.plus(keyValuePair).let(::Løsninger)

    fun <T : Any> les(key: String, serializer: KSerializer<T>): T? =
        verdi[key]?.fromJson(serializer)
}
