package no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.json.toJsonElement

object Pri {
    const val TOPIC = "helsearbeidsgiver.pri"

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
        FORESPØRSEL_MOTTATT
    }
}
