package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import no.nav.helsearbeidsgiver.utils.json.jsonConfig

internal fun createHttpClient(): HttpClient =
    HttpClient(Apache5) { configure() }

internal fun HttpClientConfig<*>.configure() {
    expectSuccess = true

    install(ContentNegotiation) {
        json(jsonConfig)
    }

    install(HttpRequestRetry) {
        maxRetries = 3
        retryOnServerErrors(maxRetries)
        retryOnExceptionIf { _, cause ->
            cause.isRetryableException()
        }
        exponentialDelay()
    }

    install(HttpTimeout) {
        socketTimeoutMillis = 3000
    }
}

private fun Throwable.isRetryableException() =
    when (this) {
        is SocketTimeoutException,
        is ConnectTimeoutException,
        is HttpRequestTimeoutException,
        is java.net.SocketTimeoutException -> true
        else -> false
    }
