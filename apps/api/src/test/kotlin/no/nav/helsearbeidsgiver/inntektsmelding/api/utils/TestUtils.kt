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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import no.nav.hag.simba.kontrakt.resultat.tilgang.Tilgang
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.apiModule
import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.mock.mockConstructor

val harTilgangResultat =
    ResultJson(
        success = Tilgang.HAR_TILGANG.toJson(Tilgang.serializer()),
    )

val ikkeTilgangResultat =
    ResultJson(
        success = Tilgang.IKKE_TILGANG.toJson(Tilgang.serializer()),
    )

abstract class ApiTest : MockAuthToken() {
    val mockProducer = mockk<Producer>(relaxed = true)

    fun testApi(block: suspend TestClient.() -> Unit): Unit =
        testApplication {
            application {
                apiModule(mockProducer, mockk())
            }

            val testClient = TestClient(this, ::mockAuthToken)

            mockConstructor(RedisPoller::class) {
                testClient.block()
            }
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

    fun <T : Any> post(
        path: String,
        body: T,
        bodySerializer: KSerializer<T>,
        block: HttpRequestBuilder.() -> Unit = { withAuth() },
    ): HttpResponse =
        runBlocking {
            httpClient.post(path) {
                contentType(ContentType.Application.Json)
                setBody(body.toJson(bodySerializer))

                block()
            }
        }

    private fun HttpRequestBuilder.withAuth() {
        bearerAuth(authToken())
    }
}
