package no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import no.nav.helsearbeidsgiver.utils.json.serializer.AsStringSerializer

object Pri {
    const val TOPIC = "helsearbeidsgiver.pri"

    @Serializable(KeySerializer::class)
    enum class Key(override val str: String) : IKey {
        // Predefinerte fra rapids-and-rivers-biblioteket
        BEHOV("@behov"),
        LØSNING("@løsning"),

        // Egendefinerte
        NOTIS("notis"),
        BOOMERANG("boomerang"),
        ORGNR("orgnr"),
        FNR("fnr"),
        FORESPOERSEL_ID("forespoerselId");

        override fun toString(): String =
            str

        fun fra(message: JsonMessage): JsonElement =
            message[str].toJsonElement()

        companion object {
            internal fun fromJson(json: String): Key =
                Key.entries.firstOrNull {
                    json == it.str
                }
                    ?: throw IllegalArgumentException("Fant ingen Pri.Key med verdi som matchet '$json'.")
        }
    }

    sealed interface MessageType {
        val name: String
    }

    @Serializable
    enum class BehovType : MessageType {
        TRENGER_FORESPØRSEL
    }

    @Serializable
    enum class NotisType : MessageType {
        FORESPØRSEL_MOTTATT,
        FORESPOERSEL_BESVART
    }

    internal object KeySerializer : AsStringSerializer<Key>(
        serialName = "helsearbeidsgiver.kotlinx.felles.Pri.Key",
        parse = Key::fromJson
    )
}
