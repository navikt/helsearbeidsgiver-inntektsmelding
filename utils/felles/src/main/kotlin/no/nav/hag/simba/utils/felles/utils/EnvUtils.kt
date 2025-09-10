package no.nav.hag.simba.utils.felles.utils

fun String.fromEnv(): String =
    System.getenv(this)
        ?: throw IllegalStateException("Mangler miljøvariabel med nøkkel '$this'.")
