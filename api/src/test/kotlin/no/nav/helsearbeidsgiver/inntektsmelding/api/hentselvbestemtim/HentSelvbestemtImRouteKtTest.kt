package no.nav.helsearbeidsgiver.inntektsmelding.api.hentselvbestemtim

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Arbeidsgiverperiode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Avsender
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Bonus
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Feilregistrert
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Ferie
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Ferietrekk
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.InntektEndringAarsak
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
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
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisPermanentErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.hardcodedJson
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.jsonStrOrNull
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace
import no.nav.helsearbeidsgiver.utils.test.mock.mockConstructor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

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
    fun `gi OK med inntektsmelding`() = testApi {
        val mockTransaksjonId = UUID.randomUUID()
        val expectedInntektsmelding = mockInntektsmeldingV1()

        mockTilgang(Tilgang.HAR_TILGANG)

        coEvery { mockRedisPoller.hent(mockTransaksjonId) } returns Mock.successResult(expectedInntektsmelding)

        val response = mockConstructor(HentSelvbestemtImProducer::class) {
            every { anyConstructed<HentSelvbestemtImProducer>().publish(any()) } returns mockTransaksjonId

            get(pathMedId)
        }

        val actualJson = response.bodyAsText()

        response.status shouldBe HttpStatusCode.OK
        actualJson shouldBe Mock.successResponseJson(expectedInntektsmelding)
    }

    @Test
    fun `manglende tilgang gir 500-feil`() = testApi {
        val mockTransaksjonId = UUID.randomUUID()

        mockTilgang(Tilgang.IKKE_TILGANG)

        coEvery { mockRedisPoller.hent(mockTransaksjonId) } returns Mock.successResult(mockInntektsmeldingV1())

        val response = mockConstructor(HentSelvbestemtImProducer::class) {
            every { anyConstructed<HentSelvbestemtImProducer>().publish(any()) } returns mockTransaksjonId

            get(pathMedId)
        }

        val actualJson = response.bodyAsText()

        response.status shouldBe HttpStatusCode.InternalServerError
        actualJson shouldBe "\"Error 500: no.nav.helsearbeidsgiver.inntektsmelding.api.auth.ManglerAltinnRettigheterException\""
    }

    @Test
    fun `feilresultat gir 500-feil`() = testApi {
        val mockTransaksjonId = UUID.randomUUID()
        val expectedFeilmelding = "Du får vente til freddan'!"

        mockTilgang(Tilgang.HAR_TILGANG)

        coEvery { mockRedisPoller.hent(mockTransaksjonId) } returns Mock.failureResult(expectedFeilmelding)

        val response = mockConstructor(HentSelvbestemtImProducer::class) {
            every { anyConstructed<HentSelvbestemtImProducer>().publish(any()) } returns mockTransaksjonId

            get(pathMedId)
        }

        val actualJson = response.bodyAsText()

        response.status shouldBe HttpStatusCode.InternalServerError
        actualJson shouldBe Mock.failureResponseJson(expectedFeilmelding)
    }

    @Test
    fun `tomt resultat gir 500-feil`() = testApi {
        val mockTransaksjonId = UUID.randomUUID()
        val expectedFeilmelding = "Ukjent feil."

        mockTilgang(Tilgang.HAR_TILGANG)

        coEvery { mockRedisPoller.hent(mockTransaksjonId) } returns Mock.emptyResult()

        val response = mockConstructor(HentSelvbestemtImProducer::class) {
            every { anyConstructed<HentSelvbestemtImProducer>().publish(any()) } returns mockTransaksjonId

            get(pathMedId)
        }

        val actualJson = response.bodyAsText()

        response.status shouldBe HttpStatusCode.InternalServerError
        actualJson shouldBe Mock.failureResponseJson(expectedFeilmelding)
    }

    @Test
    fun `timeout mot redis gir 500-feil`() = testApi {
        val mockTransaksjonId = UUID.randomUUID()
        val selvbestemtId = UUID.randomUUID()
        val expectedFeilobjekt = RedisTimeoutResponse(inntektsmeldingTypeId = selvbestemtId).toJson(RedisTimeoutResponse.serializer())

        coEvery { mockRedisPoller.hent(mockTransaksjonId) } throws RedisPollerTimeoutException(UUID.randomUUID())

        val response = mockConstructor(HentSelvbestemtImProducer::class) {
            every { anyConstructed<HentSelvbestemtImProducer>().publish(any()) } returns mockTransaksjonId

            get("$pathUtenId$selvbestemtId")
        }

        val actualJson = response.bodyAsText()

        response.status shouldBe HttpStatusCode.InternalServerError
        actualJson shouldBe Mock.failureResponseJson(expectedFeilobjekt)
    }

    @Test
    fun `ukjent feil mot redis gir 500-feil`() = testApi {
        val mockTransaksjonId = UUID.randomUUID()
        val selvbestemtId = UUID.randomUUID()
        val expectedFeilobjekt = RedisPermanentErrorResponse(selvbestemtId).toJson(RedisPermanentErrorResponse.serializer())

        coEvery { mockRedisPoller.hent(mockTransaksjonId) } throws IllegalStateException()

        val response = mockConstructor(HentSelvbestemtImProducer::class) {
            every { anyConstructed<HentSelvbestemtImProducer>().publish(any()) } returns mockTransaksjonId

            get("$pathUtenId$selvbestemtId")
        }

        val actualJson = response.bodyAsText()

        response.status shouldBe HttpStatusCode.InternalServerError
        actualJson shouldBe Mock.failureResponseJson(expectedFeilobjekt)
    }

    @Test
    fun `ukjent feil gir 500-feil`() = testApi {
        val response = mockConstructor(HentSelvbestemtImProducer::class) {
            every { anyConstructed<HentSelvbestemtImProducer>().publish(any()) } throws NullPointerException()

            get(pathMedId)
        }

        val actualJson = response.bodyAsText()

        response.status shouldBe HttpStatusCode.InternalServerError
        actualJson shouldBe "\"Error 500: java.lang.NullPointerException\""
    }

    @Test
    fun `ugyldig ID i URL gir 400-feil`() = testApi {
        val response = get("${pathUtenId}ikke-en-uuid")

        response.status shouldBe HttpStatusCode.BadRequest
        response.bodyAsText() shouldBe "\"Ugyldig parameter: 'ikke-en-uuid'\""
    }

    @Test
    fun `manglende ID i URL gir 404-feil`() = testApi {
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

    fun failureResponseJson(feilmelding: String): String =
        """
        {
            "failure": {
                "error": "$feilmelding"
            }
        }
        """.removeJsonWhitespace()

    fun failureResponseJson(feilobjekt: JsonElement): String =
        """
        {
            "failure": $feilobjekt
        }
        """.removeJsonWhitespace()

    fun successResult(inntektsmelding: Inntektsmelding): JsonElement =
        ResultJson(
            success = inntektsmelding.toJson(Inntektsmelding.serializer())
        ).toJson(ResultJson.serializer())

    fun failureResult(feilmelding: String): JsonElement =
        ResultJson(
            failure = feilmelding.toJson(String.serializer())
        ).toJson(ResultJson.serializer())

    fun emptyResult(): JsonElement =
        ResultJson().toJson(ResultJson.serializer())
}

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
        "refusjon": ${refusjon?.hardcodedJson()},
        "aarsakInnsending": "$aarsakInnsending",
        "mottatt": "$mottatt"
    }
    """.removeJsonWhitespace()

private fun Inntektsmelding.Type.hardcodedJson(): String =
    when (this) {
        is Inntektsmelding.Type.Forespurt ->
            """
            {
                "type": "Forespurt",
                "id": "$id",
                "vedtaksperiodeId": "$vedtaksperiodeId"
            }
            """

        is Inntektsmelding.Type.Selvbestemt ->
            """
            {
                "type": "Selvbestemt",
                "id": "$id"
            }
            """
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
        "naturalytelser": [${naturalytelser.joinToString(transform = Naturalytelse::hardcodedJson)}],
        "endringAarsak": ${endringAarsak?.hardcodedJson()}
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
        "endringer": [${endringer.joinToString(transform = RefusjonEndring::hardcodedJson)}],
        "sluttdato": ${sluttdato.jsonStrOrNull()}
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
        is Ferie -> """{ "aarsak": "Ferie", "ferier": [${ferier.joinToString(transform = Periode::hardcodedJson)}" }"""
        Ferietrekk -> """{ "aarsak": "Ferietrekk"}"""
        is NyStilling -> """{ "aarsak": "NyStilling", "gjelderFra": "$gjelderFra" }"""
        is NyStillingsprosent -> """{ "aarsak": "NyStillingsprosent", "gjelderFra": "$gjelderFra" }"""
        Nyansatt -> """{ "aarsak": "Nyansatt" }"""
        is Permisjon -> """{ "aarsak": "Permisjon", "permisjoner": [${permisjoner.joinToString(transform = Periode::hardcodedJson)}" }"""
        is Permittering -> """{ "aarsak": "Permittering", "permitteringer": [${permitteringer.joinToString(transform = Periode::hardcodedJson)}" }"""
        is Sykefravaer -> """{ "aarsak": "Sykefravaer", "sykefravaer": [${sykefravaer.joinToString(transform = Periode::hardcodedJson)}" }"""
        is Tariffendring -> """{ "aarsak": "Tariffendring", "gjelderFra": "$gjelderFra", "bleKjent": "$bleKjent" }"""
        is VarigLoennsendring -> """{ "aarsak": "VarigLoennsendring", "gjelderFra": "$gjelderFra" }"""
    }
