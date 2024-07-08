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
import io.prometheus.client.CollectorRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.TilgangResultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.apiModule
import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.AfterEach

val harTilgangResultat = TilgangResultat(Tilgang.HAR_TILGANG).toJson(TilgangResultat.serializer())
val ikkeTilgangResultat = TilgangResultat(Tilgang.IKKE_TILGANG).toJson(TilgangResultat.serializer())

abstract class ApiTest : MockAuthToken() {
    val mockRedisPoller = mockk<RedisPoller>()

    fun testApi(block: suspend TestClient.() -> Unit): Unit =
        testApplication {
            application {
                apiModule(mockk(relaxed = true), mockRedisPoller)
            }

            val testClient = TestClient(this, ::mockAuthToken)

            testClient.block()
        }

    @AfterEach
    fun cleanupPrometheus() {
        CollectorRegistry.defaultRegistry.clear()
    }
}

class TestClient(
    appTestBuilder: ApplicationTestBuilder,
    private val authToken: () -> String,
) {
    private val httpClient =
        appTestBuilder.createClient {
            install(ContentNegotiation) {
                json(jsonConfig)
            }
        }

    fun get(
        path: String,
        block: HttpRequestBuilder.() -> Unit = { withAuth() },
    ): HttpResponse =
        runBlocking {
            httpClient.get(path) {
                block()
            }
        }

    fun post(
        path: String,
        body: JsonElement,
        block: HttpRequestBuilder.() -> Unit = { withAuth() },
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
        block: HttpRequestBuilder.() -> Unit = { withAuth() },
    ): HttpResponse =
        post(
            path,
            body.toJson(bodySerializer),
            block,
        )

    private fun HttpRequestBuilder.withAuth() {
        bearerAuth(authToken())
    }
}
