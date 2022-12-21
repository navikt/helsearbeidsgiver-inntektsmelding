package no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic

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
        VEDTAKSPERIODE_ID("vedtaksperiodeId"),
        FOM("fom"),
        TOM("tom"),
        FORESPURT_DATA("forespurtData");

        override fun toString(): String =
            str
    }

    enum class BehovType : ValueEnum {
        TRENGER_FORESPØRSEL
    }

    enum class NotisType : ValueEnum {
        FORESPØRSEL_MOTTATT
    }

    sealed interface ValueEnum {
        val name: String
    }
}
