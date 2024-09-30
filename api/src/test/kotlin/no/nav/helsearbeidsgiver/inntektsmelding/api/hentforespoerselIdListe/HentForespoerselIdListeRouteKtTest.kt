package no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoerselIdListe

import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import no.nav.helsearbeidsgiver.felles.domene.HentForespoerslerForVedtaksperiodeIdListeResultat
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespoersel
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.harTilgangResultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ikkeTilgangResultat
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class HentForespoerselIdListeRouteKtTest : ApiTest() {
    private val path = Routes.PREFIX + Routes.HENT_FORESPOERSEL_ID_LISTE

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `gir OK med forespørsel-IDer`() =
        testApi {
            val mockResultat = Mock.mockResultat()

            coEvery { mockRedisConnection.get(any()) } returnsMany
                listOf(
                    Mock.successResult(mockResultat),
                    harTilgangResultat,
                )

            val response =
                post(
                    path,
                    HentForespoerslerRequest(vedtaksperiodeIdListe = listOf(Mock.vedtaksPeriodeId1, Mock.forespoerselId2)),
                    HentForespoerslerRequest.serializer(),
                )

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.OK
            actualJson shouldBe Mock.successResponseJson()
        }

    @Test
    fun `gir OK med tom liste av forespørsel-IDer`() =
        testApi {
            val mockResultat = Mock.mockResultatMedIngenForespoersler()

            coEvery { mockRedisConnection.get(any()) } returnsMany
                listOf(
                    Mock.successResult(mockResultat),
                    harTilgangResultat,
                )

            val response =
                post(
                    path,
                    HentForespoerslerRequest(vedtaksperiodeIdListe = listOf(Mock.vedtaksPeriodeId1, Mock.forespoerselId2)),
                    HentForespoerslerRequest.serializer(),
                )

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.OK
            actualJson shouldBe Mock.successEmptyResponseJson()
        }

    @Test
    fun `manglende tilgang til organisasjon gir 403 forbidden-feil`() =
        testApi {
            val mockResultat = Mock.mockResultat()

            coEvery { mockRedisConnection.get(any()) } returnsMany
                listOf(
                    Mock.successResult(mockResultat),
                    ikkeTilgangResultat,
                )

            val response =
                post(
                    path,
                    HentForespoerslerRequest(vedtaksperiodeIdListe = listOf(Mock.vedtaksPeriodeId1, Mock.forespoerselId2)),
                    HentForespoerslerRequest.serializer(),
                )

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.Forbidden
            actualJson shouldBe "\"Mangler rettigheter for organisasjon.\""
        }

    @Test
    fun `vedtaksperiode-IDer som tilhører ulike organisasjoner gir 400-feil`() =
        testApi {
            val mockResultat = Mock.mockResultatMedUlikeOrgnr()

            coEvery { mockRedisConnection.get(any()) } returnsMany
                listOf(
                    Mock.successResult(mockResultat),
                    harTilgangResultat,
                )

            val response =
                post(
                    path,
                    HentForespoerslerRequest(vedtaksperiodeIdListe = listOf(Mock.vedtaksPeriodeId1, Mock.forespoerselId2)),
                    HentForespoerslerRequest.serializer(),
                )

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.BadRequest
            actualJson shouldBe "\"Ikke tillat å hente forespoersler som tilhører ulike arbeidsgivere.\""
        }

    @Test
    fun `ugyldig ID i request gir 400-feil`() =
        testApi {
            val ugyldigRequest =
                JsonObject(
                    mapOf(
                        HentForespoerslerRequest::vedtaksperiodeIdListe.name to
                            listOf(
                                "ikke en uuid",
                                Mock.vedtaksPeriodeId2.toString(),
                            ).toJson(String.serializer()),
                    ),
                )

            val response =
                post(
                    path,
                    ugyldigRequest,
                    JsonElement.serializer(),
                )

            val actualJson = response.bodyAsText()
            response.status shouldBe HttpStatusCode.BadRequest
            actualJson shouldBe "\"Klarte ikke lese request.\""
        }

    @Test
    fun `feilresultat gir 500-feil`() =
        testApi {
            val expectedFeilmelding = "Det e itjnå som kjem tå sæ sjøl!"

            coEvery { mockRedisConnection.get(any()) } returnsMany
                listOf(
                    Mock.failureResult(expectedFeilmelding),
                    harTilgangResultat,
                )

            val response =
                post(
                    path,
                    HentForespoerslerRequest(vedtaksperiodeIdListe = listOf(Mock.vedtaksPeriodeId1, Mock.forespoerselId2)),
                    HentForespoerslerRequest.serializer(),
                )

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.InternalServerError
            actualJson shouldBe "\"$expectedFeilmelding\""
        }

    @Test
    fun `tomt resultat gir 500-feil`() =
        testApi {
            val expectedFeilmelding = "Teknisk feil, prøv igjen senere."

            coEvery { mockRedisConnection.get(any()) } returnsMany
                listOf(
                    Mock.emptyResult(),
                    harTilgangResultat,
                )

            val response =
                post(
                    path,
                    HentForespoerslerRequest(vedtaksperiodeIdListe = listOf(Mock.vedtaksPeriodeId1, Mock.forespoerselId2)),
                    HentForespoerslerRequest.serializer(),
                )

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.InternalServerError
            actualJson shouldBe "\"$expectedFeilmelding\""
        }

    @Test
    fun `timeout mot redis gir 500-feil`() =
        testApi {
            val expectedFeilobjekt = RedisTimeoutResponse().toJson(RedisTimeoutResponse.serializer())

            coEvery { mockRedisConnection.get(any()) } throws RedisPollerTimeoutException(UUID.randomUUID())

            val response =
                post(
                    path,
                    HentForespoerslerRequest(vedtaksperiodeIdListe = listOf(Mock.vedtaksPeriodeId1, Mock.forespoerselId2)),
                    HentForespoerslerRequest.serializer(),
                )

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.InternalServerError
            actualJson shouldBe "$expectedFeilobjekt".removeJsonWhitespace()
        }

    @Test
    fun `ukjent feil mot redis gir 500-feil`() =
        testApi {
            val expectedFeilmelding = "Teknisk feil, prøv igjen senere."

            coEvery { mockRedisConnection.get(any()) } throws IllegalStateException()

            val response =
                post(
                    path,
                    HentForespoerslerRequest(vedtaksperiodeIdListe = listOf(Mock.vedtaksPeriodeId1, Mock.forespoerselId2)),
                    HentForespoerslerRequest.serializer(),
                )

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.InternalServerError
            actualJson shouldBe "\"$expectedFeilmelding\""
        }

    @Test
    fun `ukjent feil gir 500-feil`() =
        testApi {
            val expectedFeilmelding = "Teknisk feil, prøv igjen senere."
            coEvery { mockRedisConnection.get(any()) } returns harTilgangResultat andThenThrows NullPointerException()

            val response =
                post(
                    path,
                    HentForespoerslerRequest(vedtaksperiodeIdListe = listOf(Mock.vedtaksPeriodeId1, Mock.forespoerselId2)),
                    HentForespoerslerRequest.serializer(),
                )

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.InternalServerError
            actualJson shouldBe "\"$expectedFeilmelding\""
        }
}

private object Mock {
    val orgnr = Orgnr.genererGyldig()
    val vedtaksPeriodeId1 = UUID.randomUUID()
    val forespoerselId1 = UUID.randomUUID()

    val vedtaksPeriodeId2 = UUID.randomUUID()
    val forespoerselId2 = UUID.randomUUID()

    fun mockResultat(): HentForespoerslerForVedtaksperiodeIdListeResultat =
        HentForespoerslerForVedtaksperiodeIdListeResultat(
            forespoersler =
                mapOf(
                    forespoerselId1 to mockForespoersel().copy(vedtaksperiodeId = vedtaksPeriodeId1, orgnr = orgnr.toString()),
                    forespoerselId2 to mockForespoersel().copy(vedtaksperiodeId = vedtaksPeriodeId2, orgnr = orgnr.toString()),
                ),
        )

    fun mockResultatMedIngenForespoersler(): HentForespoerslerForVedtaksperiodeIdListeResultat =
        HentForespoerslerForVedtaksperiodeIdListeResultat(
            forespoersler = emptyMap(),
        )

    fun mockResultatMedUlikeOrgnr(): HentForespoerslerForVedtaksperiodeIdListeResultat =
        HentForespoerslerForVedtaksperiodeIdListeResultat(
            forespoersler =
                mapOf(
                    forespoerselId1 to mockForespoersel().copy(vedtaksperiodeId = vedtaksPeriodeId1),
                    forespoerselId2 to mockForespoersel().copy(vedtaksperiodeId = vedtaksPeriodeId2),
                ),
        )

    fun successResponseJson(): String =
        """
    [
      {
        "vedtaksperiodeId": "$vedtaksPeriodeId1",
        "forespoerselId": "$forespoerselId1"},
      {
        "vedtaksperiodeId": "$vedtaksPeriodeId2",
        "forespoerselId": "$forespoerselId2"
      }
    ]
    """.removeJsonWhitespace()

    fun successEmptyResponseJson(): String = """[]""".removeJsonWhitespace()

    fun successResult(hentForespoerslerForVedtaksperiodeIdListeResultat: HentForespoerslerForVedtaksperiodeIdListeResultat): String =
        ResultJson(
            success = hentForespoerslerForVedtaksperiodeIdListeResultat.toJson(HentForespoerslerForVedtaksperiodeIdListeResultat.serializer()),
        ).toJson(ResultJson.serializer())
            .toString()

    fun failureResult(feilmelding: String): String =
        ResultJson(
            failure = feilmelding.toJson(),
        ).toJson(ResultJson.serializer())
            .toString()

    fun emptyResult(): String = ResultJson().toJson(ResultJson.serializer()).toString()
}
