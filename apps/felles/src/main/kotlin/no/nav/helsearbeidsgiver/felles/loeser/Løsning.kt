package no.nav.helsearbeidsgiver.felles.loeser

import kotlinx.serialization.Serializable

@Serializable(LøsningSerializer::class)
sealed class Løsning<out T : Any> {
    data class Success<out T : Any> internal constructor(
        val resultat: T
    ) : Løsning<T>()

    data class Failure internal constructor(
        val feilmelding: String
    ) : Løsning<Nothing>()
}

fun <T : Any> T.toLøsningSuccess(): Løsning<T> =
    Løsning.Success(this)

fun String.toLøsningFailure(): Løsning<Nothing> =
    Løsning.Failure(this)
