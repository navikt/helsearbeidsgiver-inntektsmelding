package no.nav.helsearbeidsgiver.inntektsmelding.akkumulator

import no.nav.helsearbeidsgiver.felles.getEnvVar

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
