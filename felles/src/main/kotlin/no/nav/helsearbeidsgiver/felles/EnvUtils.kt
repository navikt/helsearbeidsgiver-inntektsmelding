package no.nav.helsearbeidsgiver.felles

fun String.fromEnv(): String =
    System.getenv(this)
        ?: throw IllegalStateException("Mangler miljøvariabel med nøkkel '$this'.")
