package no.nav.helsearbeidsgiver.inntektsmelding.api.hentselvbestemtim

import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.verifySequence
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
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.api.AvsenderSystem
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisPermanentErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.harTilgangResultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.hardcodedJson
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ikkeTilgangResultat
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace
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
    fun `gir OK med inntektsmelding`() =
        testApi {
            val expectedInntektsmelding = mockInntektsmeldingV1()
            coEvery { mockRedisConnection.get(any()) } returnsMany
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
            coEvery { mockRedisConnection.get(any()) } returnsMany
                listOf(
                    Mock.successResult(mockInntektsmeldingV1()),
                    ikkeTilgangResultat,
                )

            val response = get(pathMedId)

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.InternalServerError
            actualJson shouldBe "\"Error 500: no.nav.helsearbeidsgiver.inntektsmelding.api.auth.ManglerAltinnRettigheterException\""
        }

    @Test
    fun `feilresultat gir 500-feil`() =
        testApi {
            val expectedFeilmelding = "Du får vente til freddan'!"

            coEvery { mockRedisConnection.get(any()) } returnsMany
                listOf(
                    Mock.failureResult(expectedFeilmelding),
                    harTilgangResultat,
                )

            val response = get(pathMedId)

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.InternalServerError
            actualJson shouldBe Mock.failureResponseJson(expectedFeilmelding)
        }

    @Test
    fun `tomt resultat gir 500-feil`() =
        testApi {
            val expectedFeilmelding = "Ukjent feil."

            coEvery { mockRedisConnection.get(any()) } returnsMany
                listOf(
                    Mock.emptyResult(),
                    harTilgangResultat,
                )

            val response = get(pathMedId)

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.InternalServerError
            actualJson shouldBe Mock.failureResponseJson(expectedFeilmelding)
        }

    @Test
    fun `timeout mot redis gir 500-feil`() =
        testApi {
            val selvbestemtId = UUID.randomUUID()
            val expectedFeilobjekt = RedisTimeoutResponse(inntektsmeldingTypeId = selvbestemtId).toJson(RedisTimeoutResponse.serializer())

            coEvery { mockRedisConnection.get(any()) } throws RedisPollerTimeoutException(UUID.randomUUID())

            val response = get("$pathUtenId$selvbestemtId")

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.InternalServerError
            actualJson shouldBe Mock.failureResponseJson(expectedFeilobjekt)
        }

    @Test
    fun `ukjent feil mot redis gir 500-feil`() =
        testApi {
            val selvbestemtId = UUID.randomUUID()
            val expectedFeilobjekt = RedisPermanentErrorResponse(selvbestemtId).toJson(RedisPermanentErrorResponse.serializer())

            coEvery { mockRedisConnection.get(any()) } throws IllegalStateException()

            val response = get("$pathUtenId$selvbestemtId")

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.InternalServerError
            actualJson shouldBe Mock.failureResponseJson(expectedFeilobjekt)
        }

    @Test
    fun `ukjent feil gir 500-feil`() =
        testApi {
            coEvery { mockRedisConnection.get(any()) } returns Mock.successResult(mockInntektsmeldingV1()) andThenThrows NullPointerException()

            val response = get(pathMedId)

            val actualJson = response.bodyAsText()

            response.status shouldBe HttpStatusCode.InternalServerError
            actualJson shouldBe "\"Error 500: java.lang.NullPointerException\""
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

    fun successResult(inntektsmelding: Inntektsmelding): String =
        ResultJson(
            success = inntektsmelding.toJson(Inntektsmelding.serializer()),
        ).toJson()
            .toString()

    fun failureResult(feilmelding: String): String =
        ResultJson(
            failure = feilmelding.toJson(),
        ).toJson()
            .toString()

    fun emptyResult(): String = ResultJson().toJson().toString()
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
        "mottatt": "$mottatt",
        "vedtaksperiodeId": "$vedtaksperiodeId"
    }
    """.removeJsonWhitespace()

private fun Inntektsmelding.Type.hardcodedJson(): String =
    when (this) {
        is Inntektsmelding.Type.Forespurt ->
            """
            {
                "type": "Forespurt",
                "id": "$id",
                "avsenderSystem": ${this.avsenderSystem.hardcodedJson()}
            }
            """

        is Inntektsmelding.Type.Selvbestemt ->
            """
            {
                "type": "Selvbestemt",
                "id": "$id",
                "avsenderSystem": ${this.avsenderSystem.hardcodedJson()}
            }
            """

        is Inntektsmelding.Type.ForespurtEkstern ->
            """
            {
                "type": "ForespurtEkstern",
                "id": "$id",
                "avsenderSystem": ${this.avsenderSystem.hardcodedJson()}
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
        "naturalytelser": [${naturalytelser.joinToString(transform = Naturalytelse::hardcodedJson)}],
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
