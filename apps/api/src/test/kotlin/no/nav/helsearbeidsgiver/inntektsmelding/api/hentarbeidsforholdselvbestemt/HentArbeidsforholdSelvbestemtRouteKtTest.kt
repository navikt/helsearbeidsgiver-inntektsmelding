package no.nav.helsearbeidsgiver.inntektsmelding.api.hentarbeidsforholdselvbestemt

import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.verifySequence
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Ansettelsesforhold
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.hentarbeidsforhold.AnsettelsesforholdResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.ErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.harTilgangResultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ikkeTilgangResultat
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HentArbeidsforholdSelvbestemtRouteKtTest : ApiTest() {
    private val path = Routes.PREFIX + Routes.HENT_ARBEIDSFORHOLD_SELVBESTEMT

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `henter arbeidsforhold`() =
        testApi {
            val request = mockRequest()
            val ansettelsesforhold =
                setOf(
                    Ansettelsesforhold(
                        startdato = 11.februar,
                        sluttdato = 15.mars,
                        yrkesKode = "1234567",
                        yrkesBeskrivelse = "BARNEHAGEASSISTENT",
                        stillingsprosent = 100.0,
                    ),
                    Ansettelsesforhold(
                        startdato = 10.mars,
                        sluttdato = 1.april,
                        yrkesKode = "7654321",
                        yrkesBeskrivelse = "SYKEPLEIER",
                        stillingsprosent = 80.0,
                    ),
                    Ansettelsesforhold(
                        startdato = 3.april,
                        sluttdato = null,
                        yrkesKode = "2345678",
                        yrkesBeskrivelse = "HJELPEPLEIER",
                        stillingsprosent = 50.0,
                    ),
                    Ansettelsesforhold(
                        startdato = 1.januar,
                        sluttdato = 1.april,
                        yrkesKode = "8765432",
                        yrkesBeskrivelse = "RENHOLDER",
                        stillingsprosent = 60.0,
                    ),
                )
            val forventetResponse = HentArbeidsforholdSelvbestemtResponse(
                ansettelsesforhold = ansettelsesforhold.map(AnsettelsesforholdResponse::fra).toSet(),
            )

            coEvery { anyConstructed<RedisPoller>().hent(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    ResultJson(
                        success = ansettelsesforhold.toJson(Ansettelsesforhold.serializer().set()),
                    ),
                )

            val response = post(path, request, HentArbeidsforholdSelvbestemtRequest.serializer())

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe forventetResponse.toJson(HentArbeidsforholdSelvbestemtResponse.serializer()).toString()

            verifySequence {
                mockProducer.send(
                    key = mockPid,
                    message =
                        withArg<Map<Key, JsonElement>> {
                            it shouldContainKey Key.KONTEKST_ID
                            it.minus(Key.KONTEKST_ID) shouldContainExactly
                                mapOf(
                                    Key.EVENT_NAME to EventName.TILGANG_ORG_REQUESTED.toJson(),
                                    Key.DATA to
                                        mapOf(
                                            Key.FNR to mockPid.toJson(),
                                            Key.ORGNR_UNDERENHET to request.orgnr.toJson(),
                                        ).toJson(),
                                )
                        },
                )
                mockProducer.send(
                    key = request.sykmeldtFnr,
                    message =
                        withArg<Map<Key, JsonElement>> {
                            it shouldContainKey Key.KONTEKST_ID
                            it.minus(Key.KONTEKST_ID) shouldContainExactly
                                mapOf(
                                    Key.EVENT_NAME to EventName.HENT_ARBEIDSFORHOLD_SELVBESTEMT_REQUESTED.toJson(),
                                    Key.DATA to
                                        mapOf(
                                            Key.ORGNR_UNDERENHET to request.orgnr.toJson(),
                                            Key.SYKMELDT_FNR to request.sykmeldtFnr.toJson(),
                                            Key.PERIODE to Periode(request.fom, request.tom).toJson(Periode.serializer()),
                                        ).toJson(),
                                )
                        },
                )
            }
        }

    @Test
    fun `mangler i request gir 400-feil`() =
        testApi {
            val requestJson =
                mockRequest()
                    .toJson(HentArbeidsforholdSelvbestemtRequest.serializer())
                    .jsonObject
                    .minus(HentArbeidsforholdSelvbestemtRequest::orgnr.name)
                    .toJson()

            val response = post(path, requestJson, JsonElement.serializer())
            val responseBody = response.bodyAsText().fromJson(ErrorResponse.serializer())

            response.status shouldBe HttpStatusCode.BadRequest
            responseBody.shouldBeTypeOf<ErrorResponse.JsonSerialization>()
        }

    @Test
    fun `manglende tilgang gir 403-feil`() =
        testApi {
            coEvery { anyConstructed<RedisPoller>().hent(any()) } returns ikkeTilgangResultat

            val response = post(path, mockRequest(), HentArbeidsforholdSelvbestemtRequest.serializer())
            val responseBody = response.bodyAsText().fromJson(ErrorResponse.serializer())

            response.status shouldBe HttpStatusCode.Forbidden
            responseBody.shouldBeTypeOf<ErrorResponse.ManglerTilgang>()
        }

    @Test
    fun `timeout mot redis gir 500-feil`() =
        testApi {
            coEvery { anyConstructed<RedisPoller>().hent(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    null,
                )

            val response = post(path, mockRequest(), HentArbeidsforholdSelvbestemtRequest.serializer())
            val responseBody = response.bodyAsText().fromJson(ErrorResponse.serializer())

            response.status shouldBe HttpStatusCode.InternalServerError
            responseBody.shouldBeTypeOf<ErrorResponse.RedisTimeout>()
        }

    @Test
    fun `feilresultat gir 500-feil`() =
        testApi {
            coEvery { anyConstructed<RedisPoller>().hent(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    ResultJson(failure = "Noe gikk spinn hakkande gale!".toJson()),
                )

            val response = post(path, mockRequest(), HentArbeidsforholdSelvbestemtRequest.serializer())
            val responseBody = response.bodyAsText().fromJson(ErrorResponse.serializer())

            response.status shouldBe HttpStatusCode.InternalServerError
            responseBody.shouldBeTypeOf<ErrorResponse.Unknown>()
        }

    @Test
    fun `tomt resultat gir 500-feil`() =
        testApi {
            coEvery { anyConstructed<RedisPoller>().hent(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    ResultJson(),
                )

            val response = post(path, mockRequest(), HentArbeidsforholdSelvbestemtRequest.serializer())
            val responseBody = response.bodyAsText().fromJson(ErrorResponse.serializer())

            response.status shouldBe HttpStatusCode.InternalServerError
            responseBody.shouldBeTypeOf<ErrorResponse.Unknown>()
        }

    @Test
    fun `ukjent feil gir 500-feil`() =
        testApi {
            coEvery { anyConstructed<RedisPoller>().hent(any()) } throws NullPointerException()

            val response = post(path, mockRequest(), HentArbeidsforholdSelvbestemtRequest.serializer())
            val responseBody = response.bodyAsText().fromJson(ErrorResponse.serializer())

            response.status shouldBe HttpStatusCode.InternalServerError
            responseBody.shouldBeTypeOf<ErrorResponse.Unknown>()
        }
}

private fun mockRequest(): HentArbeidsforholdSelvbestemtRequest =
    HentArbeidsforholdSelvbestemtRequest(
        orgnr = Orgnr.genererGyldig(),
        sykmeldtFnr = Fnr.genererGyldig(),
        fom = 12.februar,
        tom = 6.april,
    )
