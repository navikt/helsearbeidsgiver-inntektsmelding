package no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic

import kotlinx.serialization.Serializable

object Pri {
    const val TOPIC = "helsearbeidsgiver.pri"

    enum class Key(val str: String) {
        // Predefinerte fra rapids-and-rivers-biblioteket
        BEHOV("@behov"),
        LØSNING("@løsning"),

        // Egendefinerte
        NOTIS("notis"),
        BOOMERANG("boomerang"),
        ORGNR("orgnr"),
        FNR("fnr"),
        FORESPOERSEL_ID("forespoerselId"),
        RESULTAT("resultat"),
        FEIL("feil");

        override fun toString(): String =
            str
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
