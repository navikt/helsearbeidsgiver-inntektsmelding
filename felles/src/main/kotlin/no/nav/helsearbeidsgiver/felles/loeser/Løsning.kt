package no.nav.helsearbeidsgiver.felles.loeser

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.json.toJsonElement

/** Ikke bruk utenfor fil 'Løsning.kt'.*/
@PublishedApi
internal val løsningMapper = customObjectMapper()

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "løsningType"
)
@JsonSubTypes(
    JsonSubTypes.Type(
        value = Løsning.Success::class,
        name = "success"
    ),
    JsonSubTypes.Type(
        value = Løsning.Failure::class,
        name = "failure"
    )
)
sealed class Løsning<out T : Any> {
    data class Success<out T : Any> internal constructor(
        val resultat: T
    ) : Løsning<T>()

    data class Failure internal constructor(
        val feilmelding: String
    ) : Løsning<Nothing>()

    fun toJson(): JsonElement =
        toJsonNode().toJsonElement()

    internal fun toJsonNode(): JsonNode =
        løsningMapper.valueToTree(this)

    companion object {
        inline fun <T : Any, reified R : Løsning<T>> JsonElement.toLøsning(): R =
            toString()
                .runCatching {
                    løsningMapper.readValue<R>(this)
                }
                .getOrElse {
                    throw LøsningDeserializationException(it)
                }
    }
}

fun <T : Any> T.toLøsningSuccess(): Løsning.Success<T> =
    Løsning.Success(this)

fun String.toLøsningFailure(): Løsning.Failure =
    Løsning.Failure(this)

class LøsningDeserializationException(
    cause: Throwable
) : SerializationException(
    message = "Deserialisering fra JsonElement til Løsning feilet.",
    cause = cause
)
