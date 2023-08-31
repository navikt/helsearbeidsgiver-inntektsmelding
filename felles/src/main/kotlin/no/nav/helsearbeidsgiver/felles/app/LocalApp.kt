package no.nav.helsearbeidsgiver.felles.app

open class LocalApp {

    open fun setupEnvironment(memberId: String, httpPort: Int): Map<String, String> {
        val env = mapOf(
            "KAFKA_BOOTSTRAP_SERVERS" to "PLAINTEXT://localhost:9092",
            "KAFKA_CONSUMER_GROUP_ID" to memberId,
            "KAFKA_RAPID_TOPIC" to "helsearbeidsgiver.rapid",
            "IDPORTEN_AUDIENCE" to "aud-localhost",
            "IDPORTEN_WELL_KNOWN_URL" to "https://fakedings.dev-gcp.nais.io/default/.well-known/openid-configuration",
            "NAIS_APP_NAME" to "nimrod",
            "REDIS_URL" to "redis://localhost:6379/0",
            "REDIS_HOST" to "localhost",
            "HTTP_PORT" to "" + httpPort
        )
        env.forEach { (k, v) -> System.setProperty(k, v) }
        return env
    }
}
