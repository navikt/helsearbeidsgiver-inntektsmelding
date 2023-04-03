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
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import io.mockk.mockkConstructor
import no.nav.helsearbeidsgiver.felles.json.configure
import no.nav.helsearbeidsgiver.felles.test.mock.MockUuid
import no.nav.helsearbeidsgiver.felles.test.mock.mockConstructor
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.apiModule
import no.nav.helsearbeidsgiver.inntektsmelding.api.authorization.AltinnAuthorizer
import no.nav.helsearbeidsgiver.inntektsmelding.api.cache.LocalCache

abstract class ApiTest : MockAuthToken() {
    fun testApi(block: suspend TestClient.() -> Unit): Unit = testApplication {
        System.setProperty("ALTINN_URL", "test.no")
        System.setProperty("ALTINN_SERVICE_CODE", "1234")
        System.setProperty("ALTINN_API_GW_API_KEY", "test1234")
        System.setProperty("ALTINN_API_KEY", "test123")
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
    val authToken: () -> String,
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
        block: HttpRequestBuilder.() -> Unit = { withAuth() },
    ): HttpResponse =
        MockUuid.with {
            httpClient.get(path) {
                block()
            }
        }

    fun post(
        path: String,
        body: Any,
        block: HttpRequestBuilder.() -> Unit = { withAuth() },
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
