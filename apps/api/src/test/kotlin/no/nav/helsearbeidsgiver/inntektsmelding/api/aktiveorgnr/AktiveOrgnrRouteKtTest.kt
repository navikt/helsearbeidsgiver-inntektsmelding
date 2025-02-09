package no.nav.helsearbeidsgiver.inntektsmelding.api.aktiveorgnr

import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import no.nav.helsearbeidsgiver.felles.domene.AktiveArbeidsgivere
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AktiveOrgnrRouteKtTest : ApiTest() {
    private val path = Routes.PREFIX + Routes.AKTIVEORGNR

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `skal godta og returnere liste med organisasjoner`() =
        testApi {
            coEvery { mockRedisConnection.get(any()) } returns
                ResultJson(
                    success = Mock.GYLDIG_AKTIVE_ORGNR_RESPONSE.parseJson(),
                ).toJson()
                    .toString()

            val requestBody = """
            {"identitetsnummer":"${Fnr.genererGyldig()}"}
        """

            val response = post(path, requestBody.fromJson(AktiveOrgnrRequest.serializer()), AktiveOrgnrRequest.serializer())

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals(Mock.GYLDIG_AKTIVE_ORGNR_RESPONSE, response.bodyAsText())
        }

    @Test
    fun `gir 404 dersom ingen arbeidsforhold finnes`() =
        testApi {
            val resultatUtenArbeidsforhold =
                AktiveArbeidsgivere(
                    fulltNavn = "Johnny Jobblaus",
                    avsenderNavn = "Håvard Hå-Err",
                    underenheter = emptyList(),
                )

            coEvery { mockRedisConnection.get(any()) } returns
                ResultJson(
                    success = resultatUtenArbeidsforhold.toJson(AktiveArbeidsgivere.serializer()),
                ).toJson()
                    .toString()

            val response = post(path, AktiveOrgnrRequest(Fnr.genererGyldig()), AktiveOrgnrRequest.serializer())

            response.status shouldBe HttpStatusCode.NotFound
            response.bodyAsText() shouldBe "\"Fant ingen arbeidsforhold.\""
        }

    @Test
    fun `test request data`() {
        val fnr = Fnr.genererGyldig()
        val requestBody =
            """
            {"identitetsnummer":"$fnr"}
        """.removeJsonWhitespace()

        val requestObj = requestBody.fromJson(AktiveOrgnrRequest.serializer())
        assertEquals(fnr, requestObj.identitetsnummer)
    }

    private object Mock {
        val GYLDIG_AKTIVE_ORGNR_RESPONSE =
            """
            {
                "fulltNavn": "test-navn",
                "avsenderNavn": "Arild Avsender",
                "underenheter": [{"orgnrUnderenhet": "test-orgnr", "virksomhetsnavn": "test-orgnavn"}]
            }
        """.removeJsonWhitespace()
    }
}
