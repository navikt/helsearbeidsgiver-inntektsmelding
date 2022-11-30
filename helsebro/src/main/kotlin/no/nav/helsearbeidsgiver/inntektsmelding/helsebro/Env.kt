package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import no.nav.helsearbeidsgiver.felles.fromEnv

object Env {
    object Kafka {
        val brokers = "KAFKA_BROKERS".fromEnv()
        val keystorePath = "KAFKA_KEYSTORE_PATH".fromEnv()
        val truststorePath = "KAFKA_TRUSTSTORE_PATH".fromEnv()
        val credstorePassword = "KAFKA_CREDSTORE_PASSWORD".fromEnv()
    }
}
