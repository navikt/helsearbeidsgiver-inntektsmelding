package no.nav.helsearbeidsgiver.felles.loeser

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.readValue

sealed class Løsning<out T : Any> {
    companion object {
        fun <T : Any> read(objectMapper: ObjectMapper, json: String): Løsning<T> {
            listOf(
                { objectMapper.read<LøsningSuccess<T>>(json) },
                { objectMapper.read<LøsningFailure>(json) }
            )
                // Returner første suksess eller send feil videre
                .map { readFn ->
                    readFn().fold(
                        { return it },
                        { it }
                    )
                }
                .let {
                    throw SealedClassMismatchedInputException(it)
                }
        }

        private inline fun <reified T : Any> ObjectMapper.read(json: String): Result<T> =
            runCatching { readValue(json) }
    }
}

data class LøsningSuccess<out T : Any>(
    val resultat: T
) : Løsning<T>()

data class LøsningFailure(
    val feilmelding: String
) : Løsning<Nothing>()

class SealedClassMismatchedInputException(subclassJsonErrors: List<Throwable>) : MismatchedInputException(
    null,
    subclassJsonErrors.joinToString {
        it.message
            ?: "Ukjent feil med deserialisering av sealed class."
    }
)
