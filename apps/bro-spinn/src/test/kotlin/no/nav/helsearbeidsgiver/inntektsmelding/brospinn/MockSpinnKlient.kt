package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.every
import kotlinx.coroutines.delay
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic

fun mockSpinnKlient(vararg responses: Pair<HttpStatusCode, String>): SpinnKlient {
    val mockEngine =
        MockEngine.create {
            reuseHandlers = false
            requestHandlers.addAll(
                responses.map { (status, content) ->
                    {
                        if (content == "timeout") {
                            delay(3100)
                        }
                        respond(
                            content = content,
                            status = status,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }
                },
            )
        }

    val mockHttpClient = HttpClient(mockEngine) { configure() }

    return mockStatic(::createHttpClient) {
        every { createHttpClient() } returns mockHttpClient
        SpinnKlient("url") { "mock access token" }
    }
}
