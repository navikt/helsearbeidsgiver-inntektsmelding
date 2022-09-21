package no.nav.helsearbeidsgiver.inntektsmelding.api

fun setUpEnvironment(): Environment {
    return Environment(
        raw = System.getenv(),
        Dokarkiv(getEnvVar("DOKARKIV_URL", "")),
        Redis(getEnvVar("REDIS_URL", ""))
    )
}

data class Environment(
    val raw: Map<String, String>,
    val dokarkiv: Dokarkiv,
    val redis: Redis,
)

data class Dokarkiv(
    val url: String
)

data class Redis(
    val url: String
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Environment: Missing required variable \"$varName\"")
