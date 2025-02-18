package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.felles.utils.fromEnv
import no.nav.helsearbeidsgiver.inntektsmelding.altinn.Mock.altinnOrganisasjoner
import no.nav.helsearbeidsgiver.inntektsmelding.altinn.Mock.innkommendeMelding
import no.nav.helsearbeidsgiver.inntektsmelding.altinn.Mock.toMap
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.assertDoesNotThrow

class AltinnAppTest :
    FunSpec({
        val testRapid = TestRapid()
        lateinit var server: MockWebServer
        beforeEach {
            server = MockWebServer()
            server.start()
        }
        afterEach {
            clearAllMocks()
            unmockkStatic("no.nav.helsearbeidsgiver.felles.utils.EnvUtilsKt")
            testRapid.reset()
            server.shutdown()
        }

        // Mocking av RapidApplication fungerer ikke med siste versjon. Deaktiverer denne testen og undersøker mer senere.
        xtest("tester at Altinn client og maskinporten kaller riktig endepunkt og sender riktig data") {
            mockkObject(RapidApplication)
            every { RapidApplication.create(any()) } returns testRapid

            server.enqueue(
                MockResponse()
                    .addHeader("Content-Type", "application/json"),
            )
            val altinnResponse = altinnOrganisasjoner.toJson(String.serializer().set()).toString()
            val mockResponse =
                MockResponse()
                    .setBody(altinnResponse)
                    .addHeader("Content-Type", "application/json")
            server.enqueue(mockResponse)

            mockEnv(server)

            assertDoesNotThrow { main() }

            val innkommendeMelding = innkommendeMelding()

            testRapid.sendJson(innkommendeMelding.toMap())

            val tokenRequest = server.takeRequest()
            tokenRequest.path shouldBe "/token"
            tokenRequest.method shouldBe "POST"

            val altinnRequest = server.takeRequest()
            altinnRequest.path shouldStartWith "/altinn/reportees/"
            altinnRequest.method shouldBe "GET"
        }
    })

private fun mockEnv(server: MockWebServer) {
    mockkStatic("no.nav.helsearbeidsgiver.felles.utils.EnvUtilsKt")

    every { "ALTINN_TILGANGER_BASE_URL".fromEnv() } returns server.url("/altinn").toString()
    every { "ALTINN_SERVICE_CODE".fromEnv() } returns "4936"
    every { "ALTINN_TILGANGER_SCOPE".fromEnv() } returns "test:test/test"
}
