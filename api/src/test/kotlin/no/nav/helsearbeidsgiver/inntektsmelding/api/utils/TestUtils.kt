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
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.TilgangData
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.apiModule
import no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang.TilgangProducer
import no.nav.helsearbeidsgiver.utils.json.jsonIgnoreUnknown
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.mock.mockConstructor
import java.util.UUID

abstract class ApiTest : MockAuthToken() {
    fun testApi(block: suspend TestClient.() -> Unit): Unit = testApplication {
        application {
            apiModule(mockk(relaxed = true))
        }

        val testClient = TestClient(this) { mockAuthToken() }

        mockConstructor(RedisPoller::class) {
            mockConstructor(TilgangProducer::class) {
                testClient.block()
            }
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

    fun mockTilgang(tilgang: Tilgang) {
        val mockTilgangClientId = UUID.randomUUID()

        every { anyConstructed<TilgangProducer>().publish(any(), any()) } returns mockTilgangClientId

        coEvery { anyConstructed<RedisPoller>().hent(mockTilgangClientId) } returns TilgangData(tilgang).toJson(TilgangData.serializer())
    }

    fun get(
        path: String,
        block: HttpRequestBuilder.() -> Unit = { withAuth() }
    ): HttpResponse =
        runBlocking {
            httpClient.get(path) {
                block()
            }
        }

    fun post(
        path: String,
        body: JsonElement,
        block: HttpRequestBuilder.() -> Unit = { withAuth() }
    ): HttpResponse =
        runBlocking {
            httpClient.post(path) {
                contentType(ContentType.Application.Json)
                setBody(body)

                block()
            }
        }

    fun <T : Any> post(
        path: String,
        body: T,
        bodySerializer: KSerializer<T>,
        block: HttpRequestBuilder.() -> Unit = { withAuth() }
    ): HttpResponse =
        post(
            path,
            body.toJson(bodySerializer),
            block
        )

    private fun HttpRequestBuilder.withAuth() {
        bearerAuth(authToken())
    }
}
