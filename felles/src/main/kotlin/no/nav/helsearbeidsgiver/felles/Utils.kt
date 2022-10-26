package no.nav.helsearbeidsgiver.felles

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Environment: Missing required variable \"$varName\"")

fun String.fromEnv(): String =
    getEnvVar(this)

fun isLocal(): Boolean =
    getEnvVar("KTOR_ENV", "local") == "local"
