package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import no.nav.helsearbeidsgiver.felles.fromEnv
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer

object Env {
    object Kafka : PriProducer.Env {
        override val brokers = "KAFKA_BROKERS".fromEnv()
        override val keystorePath = "KAFKA_KEYSTORE_PATH".fromEnv()
        override val truststorePath = "KAFKA_TRUSTSTORE_PATH".fromEnv()
        override val credstorePassword = "KAFKA_CREDSTORE_PASSWORD".fromEnv()
    }
}
