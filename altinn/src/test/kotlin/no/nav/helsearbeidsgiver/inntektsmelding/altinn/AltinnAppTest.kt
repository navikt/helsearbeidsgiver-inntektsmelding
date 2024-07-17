package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.fromEnv
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.altinn.Mock.altinnOrganisasjoner
import no.nav.helsearbeidsgiver.inntektsmelding.altinn.Mock.innkommendeMelding
import no.nav.helsearbeidsgiver.inntektsmelding.altinn.Mock.toMap
import no.nav.helsearbeidsgiver.maskinporten.TokenResponse
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
            val baseUrl = server.url("/").toString()
            println("baseUrl== $baseUrl")
        }
        afterEach { clearAllMocks() }

        test("tester at Altinn client  og maskinporten kaller riktig endepunkt og sender riktig data") {
            mockkObject(RapidApplication)
            every { RapidApplication.create(any()) } returns testRapid

            val maskinportenToken = TokenResponse("test_token", "Bearer", 3600, "test:test1")
            val tokenResponse = Json.encodeToString(TokenResponse.serializer(), maskinportenToken)
            server.enqueue(
                MockResponse()
                    .setBody(tokenResponse)
                    .addHeader("Content-Type", "application/json"),
            )
            val altinnResponse = Json.encodeToString(altinnOrganisasjoner)
            val mockResponse =
                MockResponse()
                    .setBody(altinnResponse)
                    .addHeader("Content-Type", "application/json")
            server.enqueue(mockResponse)

            mockEnv(server)

            assertDoesNotThrow { main() }

            val innkommendeMelding = innkommendeMelding()

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            val altinnOrgnr =
                altinnOrganisasjoner
                    .mapNotNull { it.orgnr }
                    .toSet()

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                    Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                    Key.DATA to
                        innkommendeMelding.data
                            .plus(
                                Key.ORG_RETTIGHETER to altinnOrgnr.toJson(String.serializer().set()),
                            ).toJson(),
                )
            val tokenRequest = server.takeRequest()
            tokenRequest.path shouldBe "/token"
            tokenRequest.method shouldBe "POST"

            val altinnRequest = server.takeRequest()
            altinnRequest.path shouldStartWith "/altinn/reportees/"
            altinnRequest.method shouldBe "GET"
        }
    })

private fun mockEnv(server: MockWebServer) {
    mockkStatic("no.nav.helsearbeidsgiver.felles.EnvUtilsKt")

    every { "ALTINN_URL".fromEnv() } returns server.url("/altinn").toString()
    every { "ALTINN_SERVICE_CODE".fromEnv() } returns "4936"
    every { "MASKINPORTEN_TOKEN_ENDPOINT".fromEnv() } returns server.url("/token").toString()
    every { "MASKINPORTEN_ISSUER".fromEnv() } returns "https://test.test.no/"
    every { "MASKINPORTEN_CLIENT_JWK".fromEnv() } returns generateJWK()
    every { "MASKINPORTEN_CLIENT_ID".fromEnv() } returns "TEST_CLIENT_ID"
    every { "ALTINN_SCOPE".fromEnv() } returns "test:test/test"
    every { "ALTINN_API_KEY".fromEnv() } returns "mocked_value"
}

private fun generateJWK() = RSAKeyGenerator(2048).keyID("test-key-id").generate().toString()
