package no.nav.helsearbeidsgiver.felles.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.serializer.YearMonthSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr

val orgMapSerializer =
    MapSerializer(
        Orgnr.serializer(),
        String.serializer(),
    )

val personMapSerializer =
    MapSerializer(
        Fnr.serializer(),
        Person.serializer(),
    )

val inntektMapSerializer =
    MapSerializer(
        YearMonthSerializer,
        Double.serializer().nullable,
    )

fun EventName.toJson(): JsonElement = toJson(EventName.serializer())

fun BehovType.toJson(): JsonElement = toJson(BehovType.serializer())

fun KafkaKey.toJson(): JsonElement = toJson(KafkaKey.serializer())

fun Fnr.toJson(): JsonElement = toJson(Fnr.serializer())

fun Orgnr.toJson(): JsonElement = toJson(Orgnr.serializer())

fun ResultJson.toJson(): JsonElement = toJson(ResultJson.serializer())

fun <T> Set<T>.toJson(elementSerializer: KSerializer<T>): JsonElement =
    toJson(
        elementSerializer.set(),
    )

@JvmName("toJsonMapKeyStringValueString")
fun Map<String, String>.toJson(): JsonElement =
    toJson(
        MapSerializer(
            String.serializer(),
            String.serializer(),
        ),
    )

@JvmName("toJsonMapKeyKeyValueJsonElement")
fun Map<Key, JsonElement>.toJson(): JsonElement =
    toJson(
        MapSerializer(
            Key.serializer(),
            JsonElement.serializer(),
        ),
    )

fun JsonElement.toMap(): Map<Key, JsonElement> = fromJsonMapFiltered(Key.serializer())

fun <K : IKey, T : Any> K.lesOrNull(
    serializer: KSerializer<T>,
    melding: Map<K, JsonElement>,
): T? = melding[this]?.fromJson(serializer.nullable)

fun <K : IKey, T : Any> K.les(
    serializer: KSerializer<T>,
    melding: Map<K, JsonElement>,
): T =
    lesOrNull(serializer, melding)
        ?: throw MeldingException("Felt '$this' mangler i JSON-map.")

fun <K : IKey, T : Any> K.krev(
    krav: T,
    serializer: KSerializer<T>,
    melding: Map<K, JsonElement>,
): T =
    les(serializer, melding).also {
        if (it != krav) {
            throw MeldingException("Nøkkel '$this' har verdi '$it', som ikke matcher med påkrevd verdi '$krav'.")
        }
    }

fun Map<Key, JsonElement>.toPretty(): String = toJson().toPretty()

// Exception uten stacktrace, som er billigere å kaste
internal class MeldingException(
    message: String,
) : IllegalArgumentException(message) {
    override fun fillInStackTrace(): Throwable? = null
}
