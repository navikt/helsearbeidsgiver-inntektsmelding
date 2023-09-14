package no.nav.helsearbeidsgiver.inntektsmelding.bro.spinn.spinn

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.json.Jackson
import no.nav.inntektsmeldingkontrakt.Inntektsmelding

class SpinnKlient(
    val url: String,
    val httpClient: HttpClient,
    private val getAccessToken: () -> String
) {
    fun hentEksternInntektsmelding(inntektsmeldingId: String): EksternInntektsmelding {
        val result = runBlocking {
            val response = httpClient.get("$url/$inntektsmeldingId") {
                contentType(ContentType.Application.Json)
                bearerAuth(getAccessToken())
            }
            if (response.status != HttpStatusCode.OK) {
                throw SpinnApiException("$FIKK_SVAR_MED_RESPONSE_STATUS: ${response.status.value}")
            }
            Jackson.fromJson<Inntektsmelding>(response.bodyAsText())
        }

        if (result.avsenderSystem?.navn != null) {
            return EksternInntektsmelding(
                arkivreferanse = result.arkivreferanse,
                avsenderSystemNavn = result.avsenderSystem!!.navn!!,
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
