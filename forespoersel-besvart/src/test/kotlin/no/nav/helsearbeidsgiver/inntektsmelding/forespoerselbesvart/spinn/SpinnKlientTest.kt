package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart.spinn

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.mockk
import no.nav.helsearbeidsgiver.tokenprovider.AccessTokenProvider
import no.nav.helsearbeidsgiver.utils.test.resource.readResource

class SpinnKlientTest : FunSpec({
    val expectedJson = "gyldingRespons.json".readResource()
    var status: HttpStatusCode = HttpStatusCode.OK
    val mockEngine = MockEngine { request ->
        val r = status
        respond(
            content = expectedJson,
            status = r,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }
    val httpKlient = HttpClient(mockEngine)
    val tokenProvider = mockk<AccessTokenProvider>(relaxed = true)
    val spinnKlient = SpinnKlient("dummyUrl", httpKlient, tokenProvider::getToken)

    test("Hvis inntektsmelding ikke finnes kastes feil") {
        status = HttpStatusCode.NotFound
        shouldThrow<SpinnApiException> {
            spinnKlient.hentAvsenderSystemData("abc-1")
        }
    }
    test("Hvis inntektsmelding finnes returneres system navn") {
        status = HttpStatusCode.OK
        val result = spinnKlient.hentAvsenderSystemData("abc-1")
        result.avsenderSystemNavn shouldBe "NAV_NO"
    }
})
