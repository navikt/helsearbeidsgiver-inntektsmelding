package no.nav.helsearbeidsgiver.inntektsmelding.api.inntektselvbestemt

import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.InntektPerMaaned
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.Tekst
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.harTilgangResultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ikkeTilgangResultat
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.juni
import no.nav.helsearbeidsgiver.utils.test.date.mai
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace
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
        val expectedInntekt = Mock.inntekt

        coEvery { mockRedisPoller.hent(any()) } returnsMany listOf(
            harTilgangResultat,
            Mock.successResult(expectedInntekt)
        )

        val response = post(PATH, Mock.request, InntektSelvbestemtRequest.serializer())

        val actualJson = response.bodyAsText()

        response.status shouldBe HttpStatusCode.OK
        actualJson shouldBe Mock.successResponseJson(expectedInntekt)
    }

    @Test
    fun `manglende tilgang gir 500-feil`() = testApi {
        coEvery { mockRedisPoller.hent(any()) } returns ikkeTilgangResultat

        val response = post(PATH, Mock.request, InntektSelvbestemtRequest.serializer())

        val actualJson = response.bodyAsText()

        response.status shouldBe HttpStatusCode.InternalServerError
        actualJson shouldBe "\"Error 500: no.nav.helsearbeidsgiver.inntektsmelding.api.auth.ManglerAltinnRettigheterException\""
    }

    @Test
    fun `feilresultat gir 500-feil`() = testApi {
        val expectedFeilmelding = "Du får vente til freddan'!"

        coEvery { mockRedisPoller.hent(any()) } returnsMany listOf(
            harTilgangResultat,
            Mock.failureResult(expectedFeilmelding)
        )

        val response = post(PATH, Mock.request, InntektSelvbestemtRequest.serializer())

        val actualJson = response.bodyAsText()

        response.status shouldBe HttpStatusCode.InternalServerError
        actualJson shouldBe expectedFeilmelding.toJsonStr(String.serializer())
    }

    @Test
    fun `tomt resultat gir 500-feil`() = testApi {
        val expectedFeilmelding = Tekst.TEKNISK_FEIL_FORBIGAAENDE

        coEvery { mockRedisPoller.hent(any()) } returnsMany listOf(
            harTilgangResultat,
            Mock.emptyResult()
        )

        val response = post(PATH, Mock.request, InntektSelvbestemtRequest.serializer())

        val actualJson = response.bodyAsText()

        response.status shouldBe HttpStatusCode.InternalServerError
        actualJson shouldBe expectedFeilmelding.toJsonStr(String.serializer())
    }

    @Test
    fun `timeout mot redis gir 500-feil`() = testApi {
        val expectedFeilJson = RedisTimeoutResponse().toJsonStr(RedisTimeoutResponse.serializer())

        coEvery { mockRedisPoller.hent(any()) } returns harTilgangResultat andThenThrows RedisPollerTimeoutException(UUID.randomUUID())

        val response = post(PATH, Mock.request, InntektSelvbestemtRequest.serializer())

        val actualJson = response.bodyAsText()

        response.status shouldBe HttpStatusCode.InternalServerError
        actualJson shouldBe expectedFeilJson
    }

    @Test
    fun `ukjent feil mot redis gir 500-feil`() = testApi {
        val expectedFeilJson = "Error 500: java.lang.IllegalStateException".toJsonStr(String.serializer())

        coEvery { mockRedisPoller.hent(any()) } returns harTilgangResultat andThenThrows IllegalStateException()

        val response = post(PATH, Mock.request, InntektSelvbestemtRequest.serializer())

        val actualJson = response.bodyAsText()

        response.status shouldBe HttpStatusCode.InternalServerError
        actualJson shouldBe expectedFeilJson
    }

    @Test
    fun `ukjent feil gir 500-feil`() = testApi {
        coEvery { mockRedisPoller.hent(any()) } throws NullPointerException()

        val response = post(PATH, Mock.request, InntektSelvbestemtRequest.serializer())

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
            failure = feilmelding.toJson()
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
