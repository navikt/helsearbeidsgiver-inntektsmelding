package no.nav.helsearbeidsgiver.inntektsmelding.akkumulator

fun setUpEnvironment(): Environment {
    return Environment(
        raw = System.getenv(),
        redisUrl = getEnvVar(
            "REDIS_URL",
            "helsearbeidsgiver-redis.helsearbeidsgiver.svc.cluster.local"
        )
    )
}

data class Environment(
    val raw: Map<String, String>,
    val redisUrl: String
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Environment: Missing required variable \"$varName\"")
