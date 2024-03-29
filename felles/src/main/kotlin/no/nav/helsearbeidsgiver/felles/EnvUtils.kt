package no.nav.helsearbeidsgiver.felles

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getProperty(varName) ?: System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Environment: Missing required variable \"$varName\"")

fun String.fromEnv(): String =
    getEnvVar(this)
