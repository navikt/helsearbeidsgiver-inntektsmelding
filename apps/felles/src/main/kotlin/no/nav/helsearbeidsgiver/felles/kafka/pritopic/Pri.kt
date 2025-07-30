package no.nav.helsearbeidsgiver.felles.kafka.pritopic

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.utils.json.serializer.AsStringSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty

object Pri {
    const val TOPIC = "helsearbeidsgiver.pri"

    @Serializable(KeySerializer::class)
    enum class Key : IKey {
        // Predefinerte fra rapids-and-rivers-biblioteket
        BEHOV,
        LOESNING,

        // Egendefinerte
        BOOMERANG,
        FNR,
        FORESPOERSEL,
        FORESPOERSEL_ID,
        NOTIS,
        ORGNR,
        SKAL_HA_PAAMINNELSE,
        SPINN_INNTEKTSMELDING_ID,
        VEDTAKSPERIODE_ID_LISTE,
        ;

        override fun toString(): String =
            when (this) {
                BEHOV -> "@behov"
                LOESNING -> "@løsning"
                FORESPOERSEL_ID -> "forespoerselId"
                SPINN_INNTEKTSMELDING_ID -> "spinnInntektsmeldingId"
                else -> name.lowercase()
            }

        companion object {
            internal fun fromJson(key: String): Key =
                Key.entries.firstOrNull {
                    key == it.toString()
                }
                    ?: throw IllegalArgumentException("Fant ingen Pri.Key med verdi som matchet '$key'.")
        }
    }

    sealed interface MessageType {
        val name: String
    }

    @Serializable
    enum class BehovType : MessageType {
        TRENGER_FORESPØRSEL,
        HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE,
    }

    @Serializable
    enum class NotisType : MessageType {
        FORESPØRSEL_MOTTATT,
        FORESPOERSEL_BESVART,
        FORESPOERSEL_BESVART_SIMBA,
        FORESPOERSEL_FORKASTET,
        FORESPOERSEL_KASTET_TIL_INFOTRYGD,
    }

    internal object KeySerializer : AsStringSerializer<Key>(
        serialName = "helsearbeidsgiver.kotlinx.felles.Pri.Key",
        parse = Key::fromJson,
    )
}

fun Map<Pri.Key, JsonElement>.toJson(): JsonElement =
    toJson(
        MapSerializer(
            Pri.Key.serializer(),
            JsonElement.serializer(),
        ),
    )

fun Map<Pri.Key, JsonElement>.toPretty(): String = toJson().toPretty()
