package no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoerselIder

import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import no.nav.helsearbeidsgiver.felles.domene.HentForespoerselIderResultat
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.domene.VedtaksperiodeIdForespoerselIdPar
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoerselider.HentForespoerselIderRequest
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

class HentForespoerselIderRouteKtTest : ApiTest() {
    private val path = Routes.PREFIX + Routes.HENT_FORESPOERSEL_IDER

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `gir OK med forespørsel-IDer`() =
        testApi {
            val expectedForespoerselIder = Mock.mockForespoerselIder()

            coEvery { mockRedisConnection.get(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    Mock.successResult(expectedForespoerselIder),
                )

            val response =
                post(
                    path,
                    HentForespoerselIderRequest(orgnr = Orgnr.genererGyldig(), vedtaksperiodeIder = listOf(Mock.vedtaksPeriodeId1, Mock.forespoerselId2)),
                    HentForespoerselIderRequest.serializer(),
                )

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.OK
            actualJson shouldBe Mock.successResponseJson()
        }

    @Test
    fun `manglende tilgang gir 403 forbidden-feil`() =
        testApi {
            val expectedForespoerselIder = Mock.mockForespoerselIder()

            coEvery { mockRedisConnection.get(any()) } returnsMany
                listOf(
                    ikkeTilgangResultat,
                    Mock.successResult(expectedForespoerselIder),
                )

            val response =
                post(
                    path,
                    HentForespoerselIderRequest(orgnr = Orgnr.genererGyldig(), vedtaksperiodeIder = listOf(Mock.vedtaksPeriodeId1, Mock.forespoerselId2)),
                    HentForespoerselIderRequest.serializer(),
                )

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.Forbidden
            actualJson shouldBe "\"Mangler rettigheter for organisasjon.\""
        }

    @Test
    fun `feilresultat gir 500-feil`() =
        testApi {
            val expectedFeilmelding = "Det e itjnå som kjem tå sæ sjøl!"

            coEvery { mockRedisConnection.get(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    Mock.failureResult(expectedFeilmelding),
                )

            val response =
                post(
                    path,
                    HentForespoerselIderRequest(orgnr = Orgnr.genererGyldig(), vedtaksperiodeIder = listOf(Mock.vedtaksPeriodeId1, Mock.forespoerselId2)),
                    HentForespoerselIderRequest.serializer(),
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
                    harTilgangResultat,
                    Mock.emptyResult(),
                )

            val response =
                post(
                    path,
                    HentForespoerselIderRequest(orgnr = Orgnr.genererGyldig(), vedtaksperiodeIder = listOf(Mock.vedtaksPeriodeId1, Mock.forespoerselId2)),
                    HentForespoerselIderRequest.serializer(),
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
                    HentForespoerselIderRequest(orgnr = Orgnr.genererGyldig(), vedtaksperiodeIder = listOf(Mock.vedtaksPeriodeId1, Mock.forespoerselId2)),
                    HentForespoerselIderRequest.serializer(),
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
                    HentForespoerselIderRequest(orgnr = Orgnr.genererGyldig(), vedtaksperiodeIder = listOf(Mock.vedtaksPeriodeId1, Mock.forespoerselId2)),
                    HentForespoerselIderRequest.serializer(),
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
                    HentForespoerselIderRequest(orgnr = Orgnr.genererGyldig(), vedtaksperiodeIder = listOf(Mock.vedtaksPeriodeId1, Mock.forespoerselId2)),
                    HentForespoerselIderRequest.serializer(),
                )

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.InternalServerError
            actualJson shouldBe "\"$expectedFeilmelding\""
        }

    @Test
    fun `ugyldig ID i request gir 400-feil`() =
        testApi {
            val ugyldigRequest =
                JsonObject(
                    mapOf(
                        HentForespoerselIderRequest::orgnr.name to Orgnr.genererGyldig().toJson(Orgnr.serializer()),
                        HentForespoerselIderRequest::vedtaksperiodeIder.name to
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
}

private object Mock {
    val vedtaksPeriodeId1 = UUID.randomUUID()
    val forespoerselId1 = UUID.randomUUID()

    val vedtaksPeriodeId2 = UUID.randomUUID()
    val forespoerselId2 = UUID.randomUUID()

    fun mockForespoerselIder(): HentForespoerselIderResultat =
        HentForespoerselIderResultat(
            ider =
                listOf(
                    VedtaksperiodeIdForespoerselIdPar(vedtaksperiodeId = vedtaksPeriodeId1, forespoerselId = forespoerselId1),
                    VedtaksperiodeIdForespoerselIdPar(vedtaksperiodeId = vedtaksPeriodeId2, forespoerselId = forespoerselId2),
                ),
        )

    fun successResponseJson(): String =
        """
    {
        "ider": [{
            "vedtaksperiodeId": "$vedtaksPeriodeId1",
            "forespoerselId": "$forespoerselId1"
        },
        {
            "vedtaksperiodeId": "$vedtaksPeriodeId2",
            "forespoerselId": "$forespoerselId2"
        }]
    }
    """.removeJsonWhitespace()

    fun successResult(hentForespoerselIderResultat: HentForespoerselIderResultat): String =
        ResultJson(
            success = hentForespoerselIderResultat.toJson(HentForespoerselIderResultat.serializer()),
        ).toJson(ResultJson.serializer())
            .toString()

    fun failureResult(feilmelding: String): String =
        ResultJson(
            failure = feilmelding.toJson(),
        ).toJson(ResultJson.serializer())
            .toString()

    fun emptyResult(): String = ResultJson().toJson(ResultJson.serializer()).toString()
}
