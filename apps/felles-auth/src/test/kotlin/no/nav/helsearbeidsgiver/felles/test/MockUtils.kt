package no.nav.helsearbeidsgiver.felles.test

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.every
import no.nav.helsearbeidsgiver.felles.auth.AuthClient
import no.nav.helsearbeidsgiver.felles.auth.configure
import no.nav.helsearbeidsgiver.felles.auth.createHttpClient
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic

fun mockAuthClient(
    content: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): AuthClient {
    val mockEngine =
        MockEngine {
            respond(
                content = content,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

    val mockHttpClient = HttpClient(mockEngine) { configure() }

    return mockStatic(::createHttpClient) {
        every { createHttpClient() } returns mockHttpClient
        AuthClient()
    }
}
