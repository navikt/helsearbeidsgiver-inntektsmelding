package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.mockk
import no.nav.helsearbeidsgiver.felles.json.Jackson
import no.nav.helsearbeidsgiver.tokenprovider.AccessTokenProvider
import no.nav.helsearbeidsgiver.utils.test.resource.readResource
import no.nav.inntektsmeldingkontrakt.AvsenderSystem
import no.nav.inntektsmeldingkontrakt.Inntektsmelding

class SpinnKlientTest : FunSpec({

    val expectedJson = "gyldigRespons.json".readResource()
    val expectedInntektsmelding = Jackson.fromJson<Inntektsmelding>(expectedJson)
    var status: HttpStatusCode = HttpStatusCode.OK
    var responsData: String = expectedJson
    val mockEngine = MockEngine { request ->
        val r = status
        respond(
            content = responsData,
            status = r,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }
    val httpKlient = HttpClient(mockEngine)
    val tokenProvider = mockk<AccessTokenProvider>(relaxed = true)
    val spinnKlient = SpinnKlient("dummyUrl", httpKlient, tokenProvider::getToken)

    test("Hvis inntektsmelding ikke finnes kastes feil") {
        status = HttpStatusCode.NotFound
        val exception = shouldThrowExactly<SpinnApiException> {
            spinnKlient.hentEksternInntektsmelding("abc-1")
        }
        exception.message shouldBe "$FIKK_SVAR_MED_RESPONSE_STATUS: ${HttpStatusCode.NotFound.value}"
    }

    test("Hvis inntektsmelding finnes men mangler avsenderSystemNavn kastes feil") {
        status = HttpStatusCode.OK
        responsData = Jackson.toJson(expectedInntektsmelding.copy(avsenderSystem = AvsenderSystem(null, null)))

        val exception = shouldThrowExactly<SpinnApiException> {
            spinnKlient.hentEksternInntektsmelding("abc-1")
        }
        exception.message shouldBe MANGLER_AVSENDER
    }
    test("Hvis inntektsmelding finnes returneres system navn") {
        status = HttpStatusCode.OK
        responsData = expectedJson
        val result = spinnKlient.hentEksternInntektsmelding("abc-1")
        result.avsenderSystemNavn shouldBe "NAV_NO"
    }
})
