package no.nav.helsearbeidsgiver.inntektsmelding.api.inntektselvbestemt

import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.InntektPerMaaned
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.juni
import no.nav.helsearbeidsgiver.utils.test.date.mai
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace
import no.nav.helsearbeidsgiver.utils.test.mock.mockConstructor
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

private const val PATH = Routes.PREFIX + Routes.INNTEKT_SELVBESTEMT

class InntektSelvbestemtRouteKtTest : ApiTest() {

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `gi OK med inntekt`() = testApi {
        val mockClientId = UUID.randomUUID()
        val expectedInntekt = Mock.inntekt

        mockTilgang(Tilgang.HAR_TILGANG)

        coEvery { mockRedisPoller.hent(mockClientId) } returns Mock.successResult(expectedInntekt)

        val response = mockConstructor(InntektSelvbestemtProducer::class) {
            every { anyConstructed<InntektSelvbestemtProducer>().publish(any()) } returns mockClientId

            post(PATH, Mock.request, InntektSelvbestemtRequest.serializer())
        }

        val actualJson = response.bodyAsText()

        response.status shouldBe HttpStatusCode.OK
        actualJson shouldBe Mock.successResponseJson(expectedInntekt)
    }

    @Test
    fun `manglende tilgang gir 500-feil`() = testApi {
        mockTilgang(Tilgang.IKKE_TILGANG)

        val response = post(PATH, Mock.request, InntektSelvbestemtRequest.serializer())

        val actualJson = response.bodyAsText()

        response.status shouldBe HttpStatusCode.InternalServerError
        actualJson shouldBe "\"Error 500: no.nav.helsearbeidsgiver.inntektsmelding.api.auth.ManglerAltinnRettigheterException\""
    }

    @Test
    fun `feilresultat gir 500-feil`() = testApi {
        val mockClientId = UUID.randomUUID()
        val expectedFeilmelding = "Du får vente til freddan'!"

        mockTilgang(Tilgang.HAR_TILGANG)

        coEvery { mockRedisPoller.hent(mockClientId) } returns Mock.failureResult(expectedFeilmelding)

        val response = mockConstructor(InntektSelvbestemtProducer::class) {
            every { anyConstructed<InntektSelvbestemtProducer>().publish(any()) } returns mockClientId

            post(PATH, Mock.request, InntektSelvbestemtRequest.serializer())
        }

        val actualJson = response.bodyAsText()

        response.status shouldBe HttpStatusCode.InternalServerError
        actualJson shouldBe expectedFeilmelding.toJsonStr(String.serializer())
    }

    @Test
    fun `timeout mot redis gir 500-feil`() = testApi {
        val mockClientId = UUID.randomUUID()
        val expectedFeilJson = RedisTimeoutResponse().toJsonStr(RedisTimeoutResponse.serializer())

        mockTilgang(Tilgang.HAR_TILGANG)

        coEvery { mockRedisPoller.hent(mockClientId) } throws RedisPollerTimeoutException(UUID.randomUUID())

        val response = mockConstructor(InntektSelvbestemtProducer::class) {
            every { anyConstructed<InntektSelvbestemtProducer>().publish(any()) } returns mockClientId

            post(PATH, Mock.request, InntektSelvbestemtRequest.serializer())
        }

        val actualJson = response.bodyAsText()

        response.status shouldBe HttpStatusCode.InternalServerError
        actualJson shouldBe expectedFeilJson
    }

    @Test
    fun `ukjent feil mot redis gir 500-feil`() = testApi {
        val mockClientId = UUID.randomUUID()
        val expectedFeilJson = "Error 500: java.lang.IllegalStateException".toJsonStr(String.serializer())

        mockTilgang(Tilgang.HAR_TILGANG)

        coEvery { mockRedisPoller.hent(mockClientId) } throws IllegalStateException()

        val response = mockConstructor(InntektSelvbestemtProducer::class) {
            every { anyConstructed<InntektSelvbestemtProducer>().publish(any()) } returns mockClientId

            post(PATH, Mock.request, InntektSelvbestemtRequest.serializer())
        }

        val actualJson = response.bodyAsText()

        response.status shouldBe HttpStatusCode.InternalServerError
        actualJson shouldBe expectedFeilJson
    }

    @Test
    fun `ukjent feil gir 500-feil`() = testApi {
        mockTilgang(Tilgang.HAR_TILGANG)

        val response = mockConstructor(InntektSelvbestemtProducer::class) {
            every { anyConstructed<InntektSelvbestemtProducer>().publish(any()) } throws NullPointerException()

            post(PATH, Mock.request, InntektSelvbestemtRequest.serializer())
        }

        val actualJson = response.bodyAsText()

        response.status shouldBe HttpStatusCode.InternalServerError
        actualJson shouldBe "\"Error 500: java.lang.NullPointerException\""
    }
}

private object Mock {
    val request = InntektSelvbestemtRequest(Fnr.genererGyldig(), Orgnr.genererGyldig(), 12.april)
    val inntekt = Inntekt(
        listOf(
            InntektPerMaaned(april(2018), 20000.0),
            InntektPerMaaned(mai(2018), 22000.0),
            InntektPerMaaned(juni(2018), 24000.0)
        )
    )

    fun successResponseJson(inntekt: Inntekt): String =
        inntekt.hardcodedJson()

    fun successResult(inntekt: Inntekt): JsonElement =
        ResultJson(
            success = inntekt.toJson(Inntekt.serializer())
        ).toJson(ResultJson.serializer())

    fun failureResult(feilmelding: String): JsonElement =
        ResultJson(
            failure = feilmelding.toJson(String.serializer())
        ).toJson(ResultJson.serializer())

    fun emptyResult(): JsonElement =
        ResultJson().toJson(ResultJson.serializer())
}

private fun Inntekt.hardcodedJson(): String =
    """
    {
        "bruttoinntekt": ${gjennomsnitt()},
        "tidligereInntekter": [${maanedOversikt.joinToString(transform = InntektPerMaaned::hardcodedJson)}]

    }
    """.removeJsonWhitespace()

private fun InntektPerMaaned.hardcodedJson(): String =
    """
    {
        "maaned": "$maaned",
        "inntekt": $inntekt
    }
    """
