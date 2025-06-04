package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestRetryConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import no.nav.helsearbeidsgiver.utils.json.jsonConfig

internal fun createHttpClient(): HttpClient = HttpClient(Apache5) { configure() }

internal fun HttpClientConfig<*>.configure() {
    expectSuccess = true

    install(ContentNegotiation) {
        json(jsonConfig)
    }

    install(HttpRequestRetry) { configureRetry() }

    install(HttpTimeout) {
        connectTimeoutMillis = 3000
        requestTimeoutMillis = 3000
        socketTimeoutMillis = 3000
    }
}

internal fun HttpRequestRetryConfig.configureRetry() {
    retryOnException(
        maxRetries = 5,
        retryOnTimeout = true,
    )
    exponentialDelay()
}
