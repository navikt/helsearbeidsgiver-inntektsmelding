package no.nav.helsearbeidsgiver.felles.app

open class LocalApp {

    open fun getLocalEnvironment(memberId: String, httpPort: Int): Map<String, String> {
        val env = mapOf(
            "KAFKA_BOOTSTRAP_SERVERS" to "localhost:9092",
            "KAFKA_CONSUMER_GROUP_ID" to memberId,
            "KAFKA_RAPID_TOPIC" to "helsearbeidsgiver.rapid",
            "LOGINSERVICE_IDPORTEN_AUDIENCE" to "aud-localhost",
            "LOGINSERVICE_IDPORTEN_DISCOVERY_URL" to "https://fakedings.dev-gcp.nais.io/default/.well-known/openid-configuration",
            "NAIS_APP_NAME" to "nimrod",
            "REDIS_URL" to "redis://localhost:6379/0",
            "HTTP_PORT" to "" + httpPort
        )
        env.forEach { (k, v) -> System.setProperty(k, v) }
        return env
    }
}
