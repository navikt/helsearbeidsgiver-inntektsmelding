package no.nav.helsearbeidsgiver.inntektsmelding.soeknad.klient

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.every
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import no.nav.helsearbeidsgiver.utils.cache.LocalCache
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

fun mockSoeknadKlient(
    vararg responses: Pair<HttpStatusCode, String>,
    scheduler: TestCoroutineScheduler? = null,
): SoeknadKlient {
    val mockEngine =
        MockEngine.create {
            if (scheduler != null) {
                // Unngår venting på delay-funksjon kallt i request handler
                dispatcher = StandardTestDispatcher(scheduler)
            }
            reuseHandlers = false
            requestHandlers.addAll(
                responses.map { (status, content) ->
                    {
                        if (content == "timeout") {
                            delay(3100.milliseconds)
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

        SoeknadKlient("baseUrl", LocalCache.Config(entryDuration = Duration.ZERO, maxEntries = 1)) { "mock access token" }
    }
}
