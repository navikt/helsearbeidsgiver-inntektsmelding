package no.nav.helsearbeidsgiver.felles.loeser

sealed class Løsning<out T : Any>

data class LøsningSuccess<out T : Any>(
    val resultat: T
) : Løsning<T>()

data class LøsningFailure(
    val feilmelding: String
) : Løsning<Nothing>()
