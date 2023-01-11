package no.nav.helsearbeidsgiver.felles.loeser

sealed class Løsning<out T : Any> {
    data class Success<out T : Any>(
        val resultat: T
    ) : Løsning<T>()

    data class Failure(
        val feilmelding: String
    ) : Løsning<Nothing>()
}

fun <T : Any> T.toLøsningSuccess(): Løsning.Success<T> =
    Løsning.Success(this)

fun String.toLøsningFailure(): Løsning.Failure =
    Løsning.Failure(this)
