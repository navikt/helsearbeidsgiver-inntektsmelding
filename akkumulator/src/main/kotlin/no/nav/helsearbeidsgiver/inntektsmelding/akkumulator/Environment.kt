package no.nav.helsearbeidsgiver.inntektsmelding.akkumulator

import no.nav.helsearbeidsgiver.felles.getEnvVar

fun setUpEnvironment(): Environment {
    return Environment(
        raw = System.getenv(),
        redisUrl = getEnvVar(
            "REDIS_URL",
            "redis://helsearbeidsgiver-redis.helsearbeidsgiver.svc.cluster.local:6379/0"
        )
    )
}

data class Environment(
    val raw: Map<String, String>,
    val redisUrl: String
)
