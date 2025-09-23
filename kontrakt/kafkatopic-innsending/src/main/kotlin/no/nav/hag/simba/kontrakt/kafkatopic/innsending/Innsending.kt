package no.nav.hag.simba.kontrakt.kafkatopic.innsending

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.utils.json.serializer.AsStringSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson

object Innsending {
    @Serializable
    enum class EventName {
        API_INNSENDING_STARTET,
        AVVIST_INNTEKTSMELDING,
    }

    @Serializable(KeySerializer::class)
    enum class Key {
        EVENT_NAME,
        KONTEKST_ID,
        DATA,
        MOTTATT,
        INNSENDING,
        AVVIST_INNTEKTSMELDING,
        ;

        override fun toString(): String =
            when (this) {
                EVENT_NAME -> "@event_name"
                else -> name.lowercase()
            }

        companion object {
            internal fun fromString(key: String): Key =
                entries.firstOrNull {
                    key == it.toString()
                }
                    ?: throw IllegalArgumentException("Fant ingen Key med verdi som matchet '$key'.")
        }
    }

    internal object KeySerializer : AsStringSerializer<Key>(
        serialName = "helsearbeidsgiver.kotlinx.felles.Innsending.Key",
        parse = Key.Companion::fromString,
    )

    fun EventName.toJson(): JsonElement = toJson(EventName.serializer())

    fun Map<Key, JsonElement>.toJson(): JsonElement =
        toJson(
            MapSerializer(
                Key.serializer(),
                JsonElement.serializer(),
            ),
        )
}
