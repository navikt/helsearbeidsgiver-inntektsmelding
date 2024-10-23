package no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.AsStringSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty

object Pri {
    const val TOPIC = "helsearbeidsgiver.pri"

    @Serializable(KeySerializer::class)
    enum class Key(
        override val str: String,
    ) : IKey {
        // Predefinerte fra rapids-and-rivers-biblioteket
        BEHOV("@behov"),
        LØSNING("@løsning"),

        // Egendefinerte
        NOTIS("notis"),
        BOOMERANG("boomerang"),
        ORGNR("orgnr"),
        FNR("fnr"),
        FORESPOERSEL_ID("forespoerselId"),
        SPINN_INNTEKTSMELDING_ID("spinnInntektsmeldingId"),
        VEDTAKSPERIODE_ID_LISTE("vedtaksperiode_id_liste"),
        SKAL_HA_PAAMINNELSE("skal_ha_paaminnelse"),
        ;

        override fun toString(): String = str

        fun fra(message: JsonMessage): JsonElement = message[str].toString().parseJson()

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

fun Map<Pri.Key, JsonElement>.toPretty(): String =
    toJson(MapSerializer(Pri.Key.serializer(), JsonElement.serializer()))
        .toPretty()
