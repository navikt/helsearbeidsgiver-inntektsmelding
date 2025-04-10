package no.nav.helsearbeidsgiver.inntektsmelding.api.lagreselvbestemtim

import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmeldingSelvbestemt
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.mock.mockSkjemaInntektsmeldingSelvbestemt
import no.nav.helsearbeidsgiver.felles.test.mock.randomDigitString
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.harTilgangResultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ikkeTilgangResultat
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class LagreSelvbestemtImRouteKtTest : ApiTest() {
    private val path = Routes.PREFIX + Routes.SELVBESTEMT_INNTEKTSMELDING

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `skal godta og returnere id ved gyldig innsending`() =
        testApi {
            val selvbestemtId = UUID.randomUUID()

            coEvery { mockRedisConnection.get(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    Mock.successResult(selvbestemtId),
                )

            val response = post(path, mockSkjemaInntektsmeldingSelvbestemt(), SkjemaInntektsmeldingSelvbestemt.serializer())

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.OK
            actualJson shouldBe Mock.successResponseJson(selvbestemtId)
        }

    @Test
    fun `skal godta og returnere id ved innsending som mangler vedtaksperiodeId`() =
        testApi {
            val selvbestemtId = UUID.randomUUID()

            coEvery { mockRedisConnection.get(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    Mock.successResult(selvbestemtId),
                )

            val skjemaJson =
                """
            {
                "selvbestemtId": "$selvbestemtId",
                "type": {
                    "type": "Selvbestemt",
                    "id": "${UUID.randomUUID()}"
                },
                "sykmeldtFnr": "${Fnr.genererGyldig()}",
                "avsender": {
                    "orgnr": "${Orgnr.genererGyldig()}",
                    "tlf": "${randomDigitString(8)}"
                },
                "sykmeldingsperioder": [{"fom": "2024-02-12", "tom": "2024-02-28"}],
                "agp": null,
                "inntekt": {
                    "beloep": 1000.10,
                    "inntektsdato": "2024-02-12",
                    "naturalytelser": [],
                    "endringAarsaker": [
                        {"aarsak": "Bonus"}
                    ]
                },
                "refusjon": null
            }
            """.removeJsonWhitespace()
                    .parseJson()

            val response = post(path, skjemaJson, JsonElement.serializer())

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.OK
            actualJson shouldBe Mock.successResponseJson(selvbestemtId)
        }

    @Test
    fun `feil i request body gir 400-feil`() =
        testApi {
            val expectedFeilmelding = "Feil under serialisering."

            coEvery { mockRedisConnection.get(any()) } returns harTilgangResultat

            val response = post(path, "ikke et skjema", String.serializer())

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.BadRequest
            actualJson shouldBe Mock.failureResponseJson(expectedFeilmelding)
        }

    @Test
    fun `valideringsfeil gir 400-feil`() =
        testApi {
            val expectedFailureResponseJson =
                """
        {
            "valideringsfeil": [
                "Sykmeldingsperioder må fylles ut",
                "Beløp må være større eller lik 0",
                "Refusjonsbeløp må være mindre eller lik inntekt"
            ],
            "error": "Feil under validering."
        }
        """.removeJsonWhitespace()

            coEvery { mockRedisConnection.get(any()) } returns harTilgangResultat

            val skjemaMedFeil =
                mockSkjemaInntektsmeldingSelvbestemt().let {
                    it.copy(
                        sykmeldingsperioder = emptyList(),
                        inntekt =
                            it.inntekt.copy(
                                beloep = -1.0,
                            ),
                    )
                }

            val response = post(path, skjemaMedFeil, SkjemaInntektsmeldingSelvbestemt.serializer())

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.BadRequest
            actualJson shouldBe expectedFailureResponseJson
        }

    @Test
    fun `manglende tilgang gir 500-feil`() =
        testApi {
            coEvery { mockRedisConnection.get(any()) } returns ikkeTilgangResultat

            val response = post(path, mockSkjemaInntektsmeldingSelvbestemt(), SkjemaInntektsmeldingSelvbestemt.serializer())

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.InternalServerError
            actualJson shouldBe "\"Error 500: no.nav.helsearbeidsgiver.inntektsmelding.api.auth.ManglerAltinnRettigheterException\""
        }

    @Test
    fun `feilresultat gir 500-feil`() =
        testApi {
            val expectedFeilmelding = "Ukjent feil."

            coEvery { mockRedisConnection.get(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    Mock.failureResult(expectedFeilmelding),
                )

            val response = post(path, mockSkjemaInntektsmeldingSelvbestemt(), SkjemaInntektsmeldingSelvbestemt.serializer())

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.InternalServerError
            actualJson shouldBe Mock.failureResponseJson(expectedFeilmelding)
        }

    @Test
    fun `skal returnere bad request hvis arbeidsforhold mangler`() =
        testApi {
            val expectedFeilmelding = "Mangler arbeidsforhold i perioden"

            coEvery { mockRedisConnection.get(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    Mock.failureResult(expectedFeilmelding),
                )

            val response = post(path, mockSkjemaInntektsmeldingSelvbestemt(), SkjemaInntektsmeldingSelvbestemt.serializer())

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.BadRequest
            actualJson shouldBe Mock.failureResponseJson(expectedFeilmelding)
        }

    @Test
    fun `tomt resultat gir 500-feil`() =
        testApi {
            val expectedFeilmelding = "Ukjent feil."

            coEvery { mockRedisConnection.get(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    Mock.emptyResult(),
                )

            val response = post(path, mockSkjemaInntektsmeldingSelvbestemt(), SkjemaInntektsmeldingSelvbestemt.serializer())

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.InternalServerError
            actualJson shouldBe Mock.failureResponseJson(expectedFeilmelding)
        }

    @Test
    fun `timeout mot redis gir 500-feil`() =
        testApi {
            val expectedFeilmelding = "Brukte for lang tid mot redis."

            coEvery { mockRedisConnection.get(any()) } returns harTilgangResultat andThenThrows RedisPollerTimeoutException(UUID.randomUUID())

            val response = post(path, mockSkjemaInntektsmeldingSelvbestemt(), SkjemaInntektsmeldingSelvbestemt.serializer())

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.InternalServerError
            actualJson shouldBe Mock.failureResponseJson(expectedFeilmelding)
        }

    @Test
    fun `ukjent feil mot redis gir 500-feil`() =
        testApi {
            val expectedFeilmelding = "Permanent feil mot redis."

            coEvery { mockRedisConnection.get(any()) } returns harTilgangResultat andThenThrows IllegalStateException()

            val response = post(path, mockSkjemaInntektsmeldingSelvbestemt(), SkjemaInntektsmeldingSelvbestemt.serializer())

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.InternalServerError
            actualJson shouldBe Mock.failureResponseJson(expectedFeilmelding)
        }

    @Test
    fun `ukjent feil gir 500-feil`() =
        testApi {
            coEvery { mockRedisConnection.get(any()) } throws NullPointerException()

            val response = post(path, mockSkjemaInntektsmeldingSelvbestemt(), SkjemaInntektsmeldingSelvbestemt.serializer())

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.InternalServerError
            actualJson shouldBe "\"Error 500: java.lang.NullPointerException\""
        }
}

private object Mock {
    fun successResponseJson(selvbestemtId: UUID): String =
        """
        {
            "selvbestemtId": "$selvbestemtId"
        }
        """.removeJsonWhitespace()

    fun failureResponseJson(feilmelding: String): String =
        """
        {
            "error": "$feilmelding"
        }
        """.removeJsonWhitespace()

    fun successResult(selvbestemtId: UUID): String =
        ResultJson(
            success = selvbestemtId.toJson(),
        ).toJson()
            .toString()

    fun failureResult(feilmelding: String): String =
        ResultJson(
            failure = feilmelding.toJson(String.serializer()),
        ).toJson()
            .toString()

    fun emptyResult(): String = ResultJson().toJson().toString()
}
