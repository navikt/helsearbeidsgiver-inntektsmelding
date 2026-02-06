package no.nav.helsearbeidsgiver.inntektsmelding.dokumentproxy

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import java.util.UUID

class PdfClient {
    private val httpClient = createHttpClient()

    suspend fun genererPDF(
        sykmeldingId: UUID,
        token: String,
    ): PdfResponse =
        try {
            val response =
                httpClient.get("${Env.lpsApiBaseurl}/intern/personbruker/sykmelding/$sykmeldingId/pdf") {
                    bearerAuth(token)
                }

            when (response.status) {
                HttpStatusCode.OK -> {
                    PdfResponse.Success(response.readRawBytes())
                }

                HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> {
                    logger.warn("Uautorisert respons ved henting av PDF for sykmeldingId: $sykmeldingId, status: ${response.status}")
                    PdfResponse.Unauthorized(response.status)
                }

                else -> {
                    logger.error("Klarte ikke å hente PDF for sykmeldingId: $sykmeldingId, status: ${response.status}")
                    sikkerLogger.error("Klarte ikke å hente PDF for sykmeldingId: $sykmeldingId, status: ${response.status}, body: ${response.bodyAsText()}")
                    PdfResponse.Failure(response.status)
                }
            }
        } catch (e: Exception) {
            logger.error("Feil ved henting av PDF for sykmeldingId: $sykmeldingId")
            sikkerLogger.error("Feil ved henting av PDF for sykmeldingId: $sykmeldingId", e)
            PdfResponse.Failure(HttpStatusCode.InternalServerError)
        }
}

sealed class PdfResponse {
    data class Success(
        val pdf: ByteArray,
    ) : PdfResponse()

    data class Failure(
        val status: HttpStatusCode,
    ) : PdfResponse()

    data class Unauthorized(
        val status: HttpStatusCode,
    ) : PdfResponse()
}

internal fun createHttpClient(): HttpClient = HttpClient(Apache5) { configure() }

internal fun HttpClientConfig<*>.configure() {
    expectSuccess = false

    install(ContentNegotiation) {
        json(jsonConfig)
    }
}
