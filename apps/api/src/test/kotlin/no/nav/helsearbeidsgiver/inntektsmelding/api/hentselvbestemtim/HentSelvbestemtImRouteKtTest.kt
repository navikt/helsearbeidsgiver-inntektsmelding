package no.nav.helsearbeidsgiver.inntektsmelding.api.hentselvbestemtim

import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.verifySequence
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.Tekst
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Avsender
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Bonus
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Feilregistrert
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Ferie
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Ferietrekk
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.InntektEndringAarsak
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Naturalytelse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.NyStilling
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.NyStillingsprosent
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Nyansatt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Permisjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Permittering
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.RedusertLoennIAgp
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Refusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.RefusjonEndring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Sykefravaer
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Sykmeldt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Tariffendring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.VarigLoennsendring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.api.AvsenderSystem
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.ErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.harTilgangResultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.hardcodedJson
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ikkeTilgangResultat
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import no.nav.hag.simba.utils.felles.domene.ArbeidsgiverperiodeUtenEksplisitteEgenmeldinger as Arbeidsgiverperiode
import no.nav.hag.simba.utils.felles.domene.InntektsmeldingIntern as Inntektsmelding

private val pathMedId =
    Routes.PREFIX +
        Routes.SELVBESTEMT_INNTEKTSMELDING_MED_ID.replaceFirst("{selvbestemtId}", UUID.randomUUID().toString())

private val pathUtenId =
    Routes.PREFIX +
        Routes.SELVBESTEMT_INNTEKTSMELDING_MED_ID.replaceFirst("{selvbestemtId}", "")

class HentSelvbestemtImRouteKtTest : ApiTest() {
    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `Fisker inneholder ikke sykmeldtes navn`() =
        testApi {
            val inntektsmelding =
                mockInntektsmeldingV1().copy(
                    type =
                        Inntektsmelding.Type.Fisker(
                            id = UUID.randomUUID(),
                        ),
                )
            coEvery { anyConstructed<RedisPoller>().hent(any()) } returnsMany
                listOf(
                    Mock.successResult(inntektsmelding),
                    harTilgangResultat,
                )
            val response = get(pathMedId)
            val responseJson = response.bodyAsText()
            val responseIm = responseJson.fromJson(ResultJson.serializer()).success?.fromJson(HentSelvbestemtImResponseSuccess.serializer())
            responseIm?.selvbestemtInntektsmelding?.sykmeldt?.navn shouldBe Tekst.UKJENT_NAVN
        }

    @Test
    fun `gir OK med inntektsmelding`() =
        testApi {
            val expectedInntektsmelding = mockSelvbestemtInntektsmelding()

            coEvery { anyConstructed<RedisPoller>().hent(any()) } returnsMany
                listOf(
                    Mock.successResult(expectedInntektsmelding),
                    harTilgangResultat,
                )

            val response = get(pathMedId)

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.OK
            actualJson shouldBe Mock.successResponseJson(expectedInntektsmelding)

            val pathId = pathMedId.substringAfterLast("/").let(UUID::fromString)

            verifySequence {
                mockProducer.send(
                    key = pathId,
                    message =
                        withArg<Map<Key, JsonElement>> {
                            it shouldContainKey Key.KONTEKST_ID
                            it.minus(Key.KONTEKST_ID) shouldContainExactly
                                mapOf(
                                    Key.EVENT_NAME to EventName.SELVBESTEMT_IM_REQUESTED.toJson(),
                                    Key.DATA to
                                        mapOf(
                                            Key.SELVBESTEMT_ID to pathId.toJson(),
                                        ).toJson(),
                                )
                        },
                )
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
                                            Key.ORGNR_UNDERENHET to expectedInntektsmelding.avsender.orgnr.toJson(),
                                        ).toJson(),
                                )
                        },
                )
            }
        }

    @Test
    fun `manglende tilgang gir 500-feil`() =
        testApi {
            coEvery { anyConstructed<RedisPoller>().hent(any()) } returnsMany
                listOf(
                    Mock.successResult(mockSelvbestemtInntektsmelding()),
                    ikkeTilgangResultat,
                )

            val response = get(pathMedId)

            val error = response.bodyAsText().fromJson(ErrorResponse.serializer())

            response.status shouldBe HttpStatusCode.InternalServerError
            error.shouldBeTypeOf<ErrorResponse.Unknown>()
        }

    @Test
    fun `feilresultat gir 500-feil`() =
        testApi {
            coEvery { anyConstructed<RedisPoller>().hent(any()) } returnsMany
                listOf(
                    Mock.failureResult("Du får vente til freddan'!"),
                    harTilgangResultat,
                )

            val response = get(pathMedId)

            val error = response.bodyAsText().fromJson(ErrorResponse.serializer())

            response.status shouldBe HttpStatusCode.InternalServerError
            error.shouldBeTypeOf<ErrorResponse.Unknown>()
        }

    @Test
    fun `tomt resultat gir 500-feil`() =
        testApi {
            coEvery { anyConstructed<RedisPoller>().hent(any()) } returnsMany
                listOf(
                    Mock.emptyResult(),
                    harTilgangResultat,
                )

            val response = get(pathMedId)

            val error = response.bodyAsText().fromJson(ErrorResponse.serializer())

            response.status shouldBe HttpStatusCode.InternalServerError
            error.shouldBeTypeOf<ErrorResponse.Unknown>()
        }

    @Test
    fun `timeout mot redis gir 500-feil`() =
        testApi {
            val selvbestemtId = UUID.randomUUID()

            coEvery { anyConstructed<RedisPoller>().hent(any()) } returns null

            val response = get("$pathUtenId$selvbestemtId")

            val error = response.bodyAsText().fromJson(ErrorResponse.serializer())

            response.status shouldBe HttpStatusCode.InternalServerError
            error.shouldBeTypeOf<ErrorResponse.RedisTimeout>()
            error.inntektsmeldingTypeId shouldBe selvbestemtId
        }

    @Test
    fun `ukjent feil gir 500-feil`() =
        testApi {
            coEvery { anyConstructed<RedisPoller>().hent(any()) } throws NullPointerException()

            val response = get(pathMedId)

            val error = response.bodyAsText().fromJson(ErrorResponse.serializer())

            response.status shouldBe HttpStatusCode.InternalServerError
            error.shouldBeTypeOf<ErrorResponse.Unknown>()
        }

    @Test
    fun `ugyldig ID i URL gir 400-feil`() =
        testApi {
            val response = get("${pathUtenId}ugyldig-selvbestemt-id")

            response.status shouldBe HttpStatusCode.BadRequest
            response.bodyAsText() shouldBe "\"Ugyldig parameter: 'ugyldig-selvbestemt-id'.\""
        }

    @Test
    fun `manglende ID i URL gir 404-feil`() =
        testApi {
            val response = get(pathUtenId)

            response.status shouldBe HttpStatusCode.NotFound
            response.bodyAsText().shouldBeEmpty()
        }
}

private object Mock {
    fun successResponseJson(inntektsmelding: Inntektsmelding): String =
        """
        {
            "success": {
                "selvbestemtInntektsmelding": ${inntektsmelding.hardcodedJson()}
            }
        }
        """.removeJsonWhitespace()

    fun successResult(inntektsmelding: Inntektsmelding): ResultJson =
        ResultJson(
            success = inntektsmelding.toJson(Inntektsmelding.serializer()),
        )

    fun failureResult(feilmelding: String): ResultJson =
        ResultJson(
            failure = feilmelding.toJson(),
        )

    fun emptyResult(): ResultJson = ResultJson()
}

private fun mockSelvbestemtInntektsmelding(): Inntektsmelding =
    mockInntektsmeldingV1().copy(
        type =
            Inntektsmelding.Type.Selvbestemt(
                id = UUID.randomUUID(),
            ),
    )

private fun Inntektsmelding.hardcodedJson(): String =
    """
    {
        "id": "$id",
        "type": ${type.hardcodedJson()},
        "sykmeldt": ${sykmeldt.hardcodedJson()},
        "avsender": ${avsender.hardcodedJson()},
        "sykmeldingsperioder": [${sykmeldingsperioder.joinToString(transform = Periode::hardcodedJson)}],
        "agp": ${agp?.hardcodedJson()},
        "inntekt": ${inntekt?.hardcodedJson()},
        "naturalytelser": [${naturalytelser.joinToString(transform = Naturalytelse::hardcodedJson)}],
        "refusjon": ${refusjon?.hardcodedJson()},
        "aarsakInnsending": "$aarsakInnsending",
        "mottatt": "$mottatt",
        "vedtaksperiodeId": "$vedtaksperiodeId"
    }
    """.removeJsonWhitespace()

private fun Inntektsmelding.Type.hardcodedJson(): String =
    when (this) {
        is Inntektsmelding.Type.Forespurt -> {
            """
            {
                "type": "Forespurt",
                "id": "$id"
            }
            """
        }

        is Inntektsmelding.Type.ForespurtEkstern -> {
            """
            {
                "type": "ForespurtEkstern",
                "id": "$id",
                "avsenderSystem": ${avsenderSystem.hardcodedJson()}
            }
            """
        }

        is Inntektsmelding.Type.Selvbestemt -> {
            """
            {
                "type": "Selvbestemt",
                "id": "$id"
            }
            """
        }

        is Inntektsmelding.Type.Fisker -> {
            """
            {
                "type": "Fisker",
                "id": "$id"
            }
            """
        }

        is Inntektsmelding.Type.UtenArbeidsforhold -> {
            """
            {
                "type": "UtenArbeidsforhold",
                "id": "$id"
            }
            """
        }

        is Inntektsmelding.Type.Behandlingsdager -> {
            """
            {
                "type": "Behandlingsdager",
                "id": "$id"
            }
            """
        }
    }

private fun Sykmeldt.hardcodedJson(): String =
    """
    {
        "fnr": "$fnr",
        "navn": "$navn"
    }
    """

private fun Avsender.hardcodedJson(): String =
    """
    {
        "orgnr": "$orgnr",
        "orgNavn": "$orgNavn",
        "navn": "$navn",
        "tlf": "$tlf"
    }
    """

private fun AvsenderSystem.hardcodedJson(): String =
    """
    {
        "orgnr": "$orgnr",
        "navn": "$navn",
        "versjon": "$versjon"
    }
    """

private fun Arbeidsgiverperiode.hardcodedJson(): String =
    """
    {
        "perioder": [${perioder.joinToString(transform = Periode::hardcodedJson)}],
        "egenmeldinger": [${egenmeldinger.joinToString(transform = Periode::hardcodedJson)}],
        "redusertLoennIAgp": ${redusertLoennIAgp?.hardcodedJson()}
    }
    """

private fun RedusertLoennIAgp.hardcodedJson(): String =
    """
    {
        "beloep": $beloep,
        "begrunnelse": "$begrunnelse"
    }
    """

private fun Inntekt.hardcodedJson(): String =
    """
    {
        "beloep": $beloep,
        "inntektsdato": "$inntektsdato",
        "endringAarsaker": [${endringAarsaker.joinToString(transform = InntektEndringAarsak::hardcodedJson)}]
    }
    """

private fun Naturalytelse.hardcodedJson(): String =
    """
    {
        "naturalytelse": "$naturalytelse",
        "verdiBeloep": $verdiBeloep,
        "sluttdato": "$sluttdato"
    }
    """

private fun Refusjon.hardcodedJson(): String =
    """
    {
        "beloepPerMaaned": $beloepPerMaaned,
        "endringer": [${endringer.joinToString(transform = RefusjonEndring::hardcodedJson)}]
    }
    """

private fun RefusjonEndring.hardcodedJson(): String =
    """
    {
        "beloep": $beloep,
        "startdato": "$startdato"
    }
    """

private fun InntektEndringAarsak.hardcodedJson(): String =
    when (this) {
        Bonus -> """{ "aarsak": "Bonus" }"""
        Feilregistrert -> """{ "aarsak": "Feilregistrert" }"""
        is Ferie -> """{ "aarsak": "Ferie", "ferier": [${ferier.joinToString(transform = Periode::hardcodedJson)}] }"""
        Ferietrekk -> """{ "aarsak": "Ferietrekk"}"""
        is NyStilling -> """{ "aarsak": "NyStilling", "gjelderFra": "$gjelderFra" }"""
        is NyStillingsprosent -> """{ "aarsak": "NyStillingsprosent", "gjelderFra": "$gjelderFra" }"""
        Nyansatt -> """{ "aarsak": "Nyansatt" }"""
        is Permisjon -> """{ "aarsak": "Permisjon", "permisjoner": [${permisjoner.joinToString(transform = Periode::hardcodedJson)}] }"""
        is Permittering -> """{ "aarsak": "Permittering", "permitteringer": [${permitteringer.joinToString(transform = Periode::hardcodedJson)}] }"""
        is Sykefravaer -> """{ "aarsak": "Sykefravaer", "sykefravaer": [${sykefravaer.joinToString(transform = Periode::hardcodedJson)}] }"""
        is Tariffendring -> """{ "aarsak": "Tariffendring", "gjelderFra": "$gjelderFra", "bleKjent": "$bleKjent" }"""
        is VarigLoennsendring -> """{ "aarsak": "VarigLoennsendring", "gjelderFra": "$gjelderFra" }"""
    }
