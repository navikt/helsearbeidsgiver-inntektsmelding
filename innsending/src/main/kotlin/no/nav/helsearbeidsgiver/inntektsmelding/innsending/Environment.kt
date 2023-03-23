package no.nav.helsearbeidsgiver.inntektsmelding.innsending

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
