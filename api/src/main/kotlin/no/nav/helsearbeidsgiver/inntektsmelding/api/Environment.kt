package no.nav.helsearbeidsgiver.inntektsmelding.api

fun setUpEnvironment(): Environment {
    return Environment(
        raw = System.getenv(),
        Dokarkiv(getEnvVar("DOKARKIV_URL", ""))
    )
}

data class Environment(
    val raw: Map<String, String>,
    val dokarkiv: Dokarkiv
)

data class Dokarkiv(
    val url: String,
)

fun isLocal(): Boolean = getEnvVar("KTOR_ENV", "local") == "local"

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Environment: Missing required variable \"$varName\"")
