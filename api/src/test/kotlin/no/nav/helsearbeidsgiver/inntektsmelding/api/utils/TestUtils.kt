package no.nav.helsearbeidsgiver.inntektsmelding.api.utils

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import no.nav.helsearbeidsgiver.felles.json.configure
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.apiModule

val mainAppConfig = ApplicationConfig("src/main/resources/application.conf")

fun testApi(block: suspend TestClient.() -> Unit): Unit = testApplication {
    environment {
        config = mainAppConfig
    }

    application {
        apiModule(mockk(relaxed = true))
    }

    val testClient = TestClient(this)

    mockConstructor(RedisPoller::class) {
        testClient.block()
    }
}

class TestClient(
    appTestBuilder: ApplicationTestBuilder
) {
    private val httpClient = appTestBuilder.createClient {
        install(ContentNegotiation) {
            jackson {
                configure()
            }
        }
    }

    fun get(
        path: String,
        block: HttpRequestBuilder.() -> Unit = HttpRequestBuilder::withAuth
    ): HttpResponse =
        MockUuid.with {
            httpClient.get(path) {
                block()
            }
        }

    fun post(
        path: String,
        body: Any,
        block: HttpRequestBuilder.() -> Unit = HttpRequestBuilder::withAuth
    ): HttpResponse =
        MockUuid.with {
            httpClient.post(path) {
                contentType(ContentType.Application.Json)
                setBody(body)

                block()
            }
        }
}

private fun HttpRequestBuilder.withAuth() {
    bearerAuth(mockAuthToken())
}
