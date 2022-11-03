package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.json.configure
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.contentNegotiation
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.routingExtra
import java.util.UUID
import kotlin.reflect.KClass

class RouteTester(
    appTestBuilder: ApplicationTestBuilder,
    redisPoller: RedisPoller,
    private val testPath: String,
    private val testRoute: RouteExtra.() -> Unit
) {
    val mockUuid: UUID = "01234567-abcd-0123-abcd-012345678901".let(UUID::fromString)

    private val testClient: HttpClient

    init {
        val connection = mockk<RapidsConnection>(relaxed = true)

        with(appTestBuilder) {
            application {
                contentNegotiation()

                routingExtra(connection, redisPoller) {
                    routeExtra("/") {
                        testRoute()
                    }
                }
            }

            testClient = createClient {
                install(ContentNegotiation) {
                    jackson {
                        configure()
                    }
                }
            }
        }
    }

    fun get(): HttpResponse =
        withMockUuid {
            testClient.get(testPath)
        }

    fun post(request: Any): HttpResponse =
        withMockUuid {
            testClient.post(testPath) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    private fun withMockUuid(callFn: suspend () -> HttpResponse): HttpResponse =
        mockStatic(UUID::class) {
            every { UUID.randomUUID() } returns mockUuid

            runBlocking {
                callFn()
            }
        }
}

fun <T> mockStatic(fn: KClass<*>, block: () -> T): T {
    mockkStatic(fn)
    return try {
        block()
    } finally {
        unmockkStatic(fn)
    }
}
