package no.nav.helsearbeidsgiver.inntektsmelding.api.hentsoeknader

import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.verifySequence
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.kontrakt.domene.forespoersel.test.mockForespoersel
import no.nav.hag.simba.kontrakt.domene.soeknad.Soeknad
import no.nav.hag.simba.kontrakt.domene.soeknad.test.mockSoeknadArbeidstaker
import no.nav.hag.simba.kontrakt.domene.soeknad.test.mockSoeknadBehandlingsdager
import no.nav.hag.simba.kontrakt.resultat.soeknad.hentSoeknaderResultatSerializer
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.valkey.ResultJson
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.ErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.harTilgangResultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.hardcodedJson
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ikkeTilgangResultat
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.juni
import no.nav.helsearbeidsgiver.utils.test.date.mai
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class HentSoeknaderRouteKtTest : ApiTest() {
    private val path = Routes.PREFIX + Routes.HENT_SOEKNADER

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `henter søknader med 200 OK`() =
        testApi {
            val request = Mock.request()
            val kategorier = Mock.kategorier()

            // Minst én av listene skal være tomme, men det bestemmes av servicen, så det testes ikke her.
            val forventetResponse =
                HentSoeknaderResponse(
                    kategorier.first.map(::ForespoerselResponse),
                    kategorier.second.map(::SoeknadArbeidstakerResponse),
                    kategorier.third.map(::SoeknadBehandlingsdagerResponse),
                )

            coEvery { anyConstructed<RedisPoller>().hent(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    Mock.successResult(kategorier),
                )

            val response = post(path, request, HentSoeknaderRequest.serializer())
            val responseBody = response.bodyAsText()

            response.status shouldBe HttpStatusCode.OK
            responseBody shouldBe forventetResponse.hardcodedJson()

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
                                    Key.EVENT_NAME to EventName.REQUEST_HENT_SOEKNAD_LISTE.toJson(),
                                    Key.DATA to
                                        mapOf(
                                            Key.ORGNR_UNDERENHET to request.orgnr.toJson(),
                                            Key.SYKMELDT_FNR to request.sykmeldtFnr.toJson(),
                                            Key.ER_BEHANDLINGSDAGER to request.erBehandlingsdager.toJson(Boolean.serializer()),
                                        ).toJson(),
                                )
                        },
                )
            }
        }

    @Test
    fun `henter søknader med 200 OK når ingen funnet`() =
        testApi {
            val request = Mock.request()

            val forventetResponse = HentSoeknaderResponse(emptyList(), emptyList(), emptyList())

            coEvery { anyConstructed<RedisPoller>().hent(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    Mock.successResult(Triple(emptyList(), emptyList(), emptyList())),
                )

            val response = post(path, request, HentSoeknaderRequest.serializer())
            val responseBody = response.bodyAsText()

            response.status shouldBe HttpStatusCode.OK
            responseBody shouldBe forventetResponse.hardcodedJson()
        }

    @Test
    fun `feil i request body gir 400-feil`() =
        testApi {
            val response = post(path, "ikke en request", String.serializer())
            val responseBody = response.bodyAsText().fromJson(ErrorResponse.serializer())

            response.status shouldBe HttpStatusCode.BadRequest
            responseBody.shouldBeTypeOf<ErrorResponse.JsonSerialization>()
        }

    @Test
    fun `manglende tilgang gir 403-feil`() =
        testApi {
            coEvery { anyConstructed<RedisPoller>().hent(any()) } returns ikkeTilgangResultat

            val response = post(path, Mock.request(), HentSoeknaderRequest.serializer())
            val responseBody = response.bodyAsText().fromJson(ErrorResponse.serializer())

            response.status shouldBe HttpStatusCode.Forbidden
            responseBody.shouldBeTypeOf<ErrorResponse.ManglerTilgang>()
        }

    @Test
    fun `feilresultat gir 500-feil`() =
        testApi {
            coEvery { anyConstructed<RedisPoller>().hent(any()) } returnsMany
                listOf(
                    harTilgangResultat,
                    Mock.failureResult("Ka farsken, e det kaffe i karsken?"),
                )

            val response = post(path, Mock.request(), HentSoeknaderRequest.serializer())
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
                    Mock.emptyResult(),
                )

            val response = post(path, Mock.request(), HentSoeknaderRequest.serializer())
            val responseBody = response.bodyAsText().fromJson(ErrorResponse.serializer())

            response.status shouldBe HttpStatusCode.InternalServerError
            responseBody.shouldBeTypeOf<ErrorResponse.Unknown>()
        }

    @Test
    fun `timeout mot redis gir 500-feil`() =
        testApi {
            coEvery { anyConstructed<RedisPoller>().hent(any()) } returns null

            val response = post(path, Mock.request(), HentSoeknaderRequest.serializer())
            val responseBody = response.bodyAsText().fromJson(ErrorResponse.serializer())

            response.status shouldBe HttpStatusCode.InternalServerError
            responseBody.shouldBeTypeOf<ErrorResponse.RedisTimeout>()
            responseBody.inntektsmeldingTypeId.shouldBeNull()
        }

    @Test
    fun `ukjent feil gir 500-feil`() =
        testApi {
            coEvery { anyConstructed<RedisPoller>().hent(any()) } throws NullPointerException()

            val response = post(path, Mock.request(), HentSoeknaderRequest.serializer())
            val responseBody = response.bodyAsText().fromJson(ErrorResponse.serializer())

            response.status shouldBe HttpStatusCode.InternalServerError
            responseBody.shouldBeTypeOf<ErrorResponse.Unknown>()
        }
}

private object Mock {
    fun kategorier(): Triple<List<Pair<UUID, Forespoersel>>, List<Soeknad.Arbeidstaker>, List<Soeknad.Behandlingsdager>> =
        Triple(
            listOf(
                UUID.randomUUID() to mockForespoersel(),
                UUID.randomUUID() to
                    mockForespoersel().copy(
                        sykmeldingsperioder =
                            listOf(
                                5.mars til 13.mars,
                                18.mars til 30.mars,
                            ),
                        egenmeldingsperioder = emptyList(),
                        erBesvart = true,
                    ),
            ),
            listOf(
                mockSoeknadArbeidstaker(),
                mockSoeknadArbeidstaker().copy(
                    sykmeldingsperiode = 6.april til 8.april,
                    egenmeldingerFraSykmelding = emptyList(),
                    erGradert = true,
                ),
                mockSoeknadArbeidstaker(),
            ),
            listOf(
                mockSoeknadBehandlingsdager(),
                mockSoeknadBehandlingsdager().copy(
                    sykmeldingsperiode = 12.mai til 12.juni,
                    behandlingsdager =
                        setOf(
                            12.mai,
                            18.mai,
                            24.mai,
                        ),
                ),
            ),
        )

    fun request(): HentSoeknaderRequest =
        HentSoeknaderRequest(
            orgnr = Orgnr.genererGyldig(),
            sykmeldtFnr = Fnr.genererGyldig(),
            erBehandlingsdager = false,
        )

    fun successResult(kategorier: Triple<List<Pair<UUID, Forespoersel>>, List<Soeknad.Arbeidstaker>, List<Soeknad.Behandlingsdager>>): ResultJson =
        ResultJson(
            success = kategorier.toJson(hentSoeknaderResultatSerializer),
        )

    fun failureResult(feilmelding: String): ResultJson =
        ResultJson(
            failure = feilmelding.toJson(),
        )

    fun emptyResult(): ResultJson = ResultJson()
}

private fun HentSoeknaderResponse.hardcodedJson(): String =
    """
    {
        "forespoersler": [${forespoersler.joinToString(transform = ForespoerselResponse::hardcodedJson)}],
        "soeknaderArbeidstaker": [${soeknaderArbeidstaker.joinToString(transform = SoeknadArbeidstakerResponse::hardcodedJson)}],
        "soeknaderBehandlingsdager": [${soeknaderBehandlingsdager.joinToString(transform = SoeknadBehandlingsdagerResponse::hardcodedJson)}]
    }
    """.removeJsonWhitespace()

private fun ForespoerselResponse.hardcodedJson(): String =
    """
    {
        "forespoerselId": "$forespoerselId",
        "sykmeldingsperioder": [${sykmeldingsperioder.joinToString(transform = Periode::hardcodedJson)}],
        "egenmeldingsperioder": [${egenmeldingsperioder.joinToString(transform = Periode::hardcodedJson)}],
        "erBesvart": $erBesvart
    }
    """

private fun SoeknadArbeidstakerResponse.hardcodedJson(): String =
    """
    {
        "sykmeldingsperiode": ${sykmeldingsperiode.hardcodedJson()},
        "egenmeldingsperioder": [${egenmeldingsperioder.joinToString(transform = Periode::hardcodedJson)}],
        "erGradert": $erGradert
    }
    """

private fun SoeknadBehandlingsdagerResponse.hardcodedJson(): String =
    """
    {
        "sykmeldingsperiode": ${sykmeldingsperiode.hardcodedJson()},
        "behandlingsdager": [${behandlingsdager.joinToString { "\"$it\"" }}]
    }
    """
