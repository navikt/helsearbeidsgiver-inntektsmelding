package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import no.nav.helsearbeidsgiver.felles.fromEnv
import no.nav.helsearbeidsgiver.felles.oauth2.AzureOAuth2Environment
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer

object Env {
    object Kafka : PriProducer.Env {
        override val brokers = "KAFKA_BROKERS".fromEnv()
        override val keystorePath = "KAFKA_KEYSTORE_PATH".fromEnv()
        override val truststorePath = "KAFKA_TRUSTSTORE_PATH".fromEnv()
        override val credstorePassword = "KAFKA_CREDSTORE_PASSWORD".fromEnv()
    }

    val spinnUrl = "SPINN_API_URL".fromEnv()

    val azureOAuthEnvironment =
        AzureOAuth2Environment(
            scope = "PROXY_SCOPE".fromEnv(),
            azureWellKnownUrl = "AZURE_APP_WELL_KNOWN_URL".fromEnv(),
            azureTokenEndpointUrl = "AZURE_OPENID_CONFIG_TOKEN_ENDPOINT".fromEnv(),
            azureAppClientID = "AZURE_APP_CLIENT_ID".fromEnv(),
            azureAppClientSecret = "AZURE_APP_CLIENT_SECRET".fromEnv(),
            azureAppJwk = "AZURE_APP_JWK".fromEnv()
        )

    val redisUrl = "REDIS_URL".fromEnv()
}
