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
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import no.nav.helsearbeidsgiver.felles.json.jsonIgnoreUnknown
import no.nav.helsearbeidsgiver.felles.test.mock.MockUuid
import no.nav.helsearbeidsgiver.felles.test.mock.mockConstructor
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.apiModule

abstract class ApiTest : MockAuthToken() {
    fun testApi(block: suspend TestClient.() -> Unit): Unit = testApplication {
        application {
            apiModule(mockk(relaxed = true))
        }

        val testClient = TestClient(this) { mockAuthToken() }

        mockConstructor(RedisPoller::class) {
            testClient.block()
        }
    }
}

class TestClient(
    appTestBuilder: ApplicationTestBuilder,
    val authToken: () -> String
) {
    private val httpClient = appTestBuilder.createClient {
        install(ContentNegotiation) {
            json(jsonIgnoreUnknown)
        }
    }

    fun get(
        path: String,
        block: HttpRequestBuilder.() -> Unit = { withAuth() }
    ): HttpResponse =
        MockUuid.with {
            httpClient.get(path) {
                block()
            }
        }

    fun post(
        path: String,
        body: Any,
        block: HttpRequestBuilder.() -> Unit = { withAuth() }
    ): HttpResponse =
        MockUuid.with {
            httpClient.post(path) {
                contentType(ContentType.Application.Json)
                setBody(body)

                block()
            }
        }

    private fun HttpRequestBuilder.withAuth() {
        bearerAuth(authToken())
    }
}
