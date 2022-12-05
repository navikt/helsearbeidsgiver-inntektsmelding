package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient

fun buildClient(
    response: String,
    status: HttpStatusCode,
    headers: Headers = headersOf(HttpHeaders.ContentType, "application/json")
): ArbeidsgiverNotifikasjonKlient {
    val mockEngine = MockEngine {
        respond(
            content = ByteReadChannel(response),
            status = status,
            headers = headers
        )
    }
    return ArbeidsgiverNotifikasjonKlient("http://127.0.0.1", HttpClient(mockEngine)) { "token" }
}
