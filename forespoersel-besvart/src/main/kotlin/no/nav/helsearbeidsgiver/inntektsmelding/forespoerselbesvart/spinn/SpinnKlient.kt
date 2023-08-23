package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart.spinn

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import no.nav.inntektsmeldingkontrakt.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.utils.AvsenderSystemData

class SpinnKlient(
    val url: String,
    val httpClient: HttpClient,
    private val getAccessToken: () -> String
) {
    fun hentAvsenderSystemData(inntektsmeldingId: String) : AvsenderSystemData {
        val result = runBlocking {
            val response = httpClient.get("$url/$inntektsmeldingId") {
                contentType(ContentType.Application.Json)
                bearerAuth(getAccessToken())
            }
            if (response.status != HttpStatusCode.OK) {
                throw SpinnApiException("Fikk svar med response status: ${response.status.value}")
            }
            response.body<Inntektsmelding>()
        }

        if (result.avsenderSystem?.navn != null) {
            return AvsenderSystemData(
                arkivreferanse = result.arkivreferanse,
                avsenderSystemNavn = result.avsenderSystem!!.navn!!,
                avsenderSystemVersjon = result.avsenderSystem?.versjon ?: ""
            )
        }
        throw SpinnApiException("Mangler avsenderSystemNavn")

    }
}
class SpinnApiException (message: String, cause: Throwable? = null) : Exception(message, cause)
