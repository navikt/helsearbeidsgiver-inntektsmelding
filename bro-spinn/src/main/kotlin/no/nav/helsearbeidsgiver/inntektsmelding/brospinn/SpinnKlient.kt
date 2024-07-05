package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import java.util.UUID

class SpinnKlient(
    private val url: String,
    private val getAccessToken: () -> String
) {
    private val httpClient = createHttpClient()
    fun hentEksternInntektsmelding(inntektsmeldingId: UUID): EksternInntektsmelding {
        val result = runBlocking {
            try {
                val response = httpClient.get("$url/$inntektsmeldingId") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(getAccessToken())
                }
                if (response.status != HttpStatusCode.OK) {
                    throw SpinnApiException("$FIKK_SVAR_MED_RESPONSE_STATUS: ${response.status.value}")
                }
                Jackson.fromJson(response.bodyAsText())
            } catch (e: ClientRequestException) {
                throw SpinnApiException("$FIKK_SVAR_MED_RESPONSE_STATUS: ${e.response.status.value}", e)
            }
        }

        val avsenderSystemNavn = result.avsenderSystem?.navn
        if (avsenderSystemNavn != null) {
            return EksternInntektsmelding(
                arkivreferanse = result.arkivreferanse,
                avsenderSystemNavn = avsenderSystemNavn,
                avsenderSystemVersjon = result.avsenderSystem?.versjon ?: "",
                tidspunkt = result.mottattDato
            )
        }
        throw SpinnApiException(MANGLER_AVSENDER)
    }
}
class SpinnApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

const val MANGLER_AVSENDER = "Mangler avsenderSystemNavn"
const val FIKK_SVAR_MED_RESPONSE_STATUS = "Fikk svar med response status"
