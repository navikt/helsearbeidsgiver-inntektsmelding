package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.FeilReport
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Forespoersel
import no.nav.helsearbeidsgiver.felles.ForespoerselType
import no.nav.helsearbeidsgiver.felles.ForespurtData
import no.nav.helsearbeidsgiver.felles.ForrigeInntekt
import no.nav.helsearbeidsgiver.felles.ForslagInntekt
import no.nav.helsearbeidsgiver.felles.ForslagRefusjon
import no.nav.helsearbeidsgiver.felles.HentForespoerselResultat
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.InntektPerMaaned
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.TilgangData
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtData
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtDataMedForrigeInntekt
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.tilgang.TilgangProducer
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.hardcodedJson
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.jsonStrOrNull
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.ValidationResponse
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.desember
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace
import no.nav.helsearbeidsgiver.utils.test.mock.mockConstructor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

private const val PATH = Routes.PREFIX + Routes.TRENGER

class TrengerRouteKtTest : ApiTest() {

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `skal returnere resultat og status CREATED når trenger virker`() = testApi {
        val mockClientId = UUID.randomUUID()
        val expectedJson = Mock.responseJson()

        mockTilgang(Tilgang.HAR_TILGANG)

        coEvery { mockRedisPoller.hent(mockClientId, any(), any()) } returns Mock.resultatOkJson

        val response = mockConstructor(TrengerProducer::class) {
            every { anyConstructed<TrengerProducer>().publish(any(), any()) } returns mockClientId

            post(PATH, Mock.request, HentForespoerselRequest.serializer())
        }

        val actualJson = response.bodyAsText()

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `skal returnere resultat og status CREATED når trenger virker med forespørsel bare inntekt`() = testApi {
        val mockClientId = UUID.randomUUID()
        val expectedJson = Mock.responseBareInntektJson()

        mockTilgang(Tilgang.HAR_TILGANG)

        coEvery { mockRedisPoller.hent(mockClientId, any(), any()) } returns Mock.resultatOkMedForrigeInntektJson

        val response = mockConstructor(TrengerProducer::class) {
            every { anyConstructed<TrengerProducer>().publish(any(), any()) } returns mockClientId

            post(PATH, Mock.request, HentForespoerselRequest.serializer())
        }

        val actualJson = response.bodyAsText()

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `skal returnere Internal server error hvis Redis timer ut`() = testApi {
        val mockClientId = UUID.randomUUID()

        mockTilgang(Tilgang.HAR_TILGANG)

        coEvery { mockRedisPoller.hent(mockClientId, any(), any()) } throws RedisPollerTimeoutException(UUID.randomUUID())

        val response = mockConstructor(TrengerProducer::class) {
            every { anyConstructed<TrengerProducer>().publish(any(), any()) } returns mockClientId

            post(PATH, Mock.request, HentForespoerselRequest.serializer())
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    @Test
    fun `skal returnere valideringsfeil ved ugyldig request`() = testApi {
        val ugyldigRequest = JsonObject(
            mapOf(
                HentForespoerselRequest::uuid.name to "ikke en uuid".toJson()
            )
        )

        val response = post(PATH, ugyldigRequest, JsonElement.serializer())

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNotNull(response.bodyAsText())

        val result = response.bodyAsText().fromJson(ResultJson.serializer())

        assertNull(result.success)
        assertNotNull(result.failure)

        val violations = result.failure!!.fromJson(ValidationResponse.serializer()).errors

        assertEquals(1, violations.size)
        assertEquals("uuid", violations[0].property)
    }

    @Test
    fun `skal returnere Forbidden hvis feil ikke tilgang`() = testApi {
        mockTilgang(Tilgang.IKKE_TILGANG)

        val response = post(PATH, Mock.request, HentForespoerselRequest.serializer())
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `skal returnere Forbidden hvis feil i Tilgangsresultet`() = testApi {
        val mockTilgangClientId = UUID.randomUUID()

        every { anyConstructed<TilgangProducer>().publishForespoerselId(any(), any()) } returns mockTilgangClientId

        coEvery { mockRedisPoller.hent(mockTilgangClientId) } returns TilgangData(
            feil = FeilReport(
                feil = mutableListOf(
                    Feilmelding("Noe er riv ruskende galt!")
                )
            )
        ).toJson(TilgangData.serializer())

        val response = post(PATH, Mock.request, HentForespoerselRequest.serializer())
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
}

private object Mock {
    val request = HentForespoerselRequest(UUID.randomUUID())

    private val forespoersel = Forespoersel(
        type = ForespoerselType.KOMPLETT,
        orgnr = "123",
        fnr = "abc",
        sykmeldingsperioder = listOf(
            1.april til 20.april,
            25.april til 30.april
        ),
        egenmeldingsperioder = listOf(
            29.mars til 29.mars,
            31.mars til 31.mars
        ),
        bestemmendeFravaersdager = mapOf("123" to 25.april),
        forespurtData = mockForespurtData(),
        erBesvart = false,
        vedtaksperiodeId = randomUuid()
    )

    private val inntekt = Inntekt(
        listOf(
            InntektPerMaaned(
                maaned = februar(2022),
                inntekt = 2.0
            ),
            InntektPerMaaned(
                maaned = januar(2022),
                inntekt = 1.0
            ),
            InntektPerMaaned(
                maaned = desember(2022),
                inntekt = 3.0
            )
        )
    )

    val resultatOkJson = ResultJson(
        success = HentForespoerselResultat(
            sykmeldtNavn = "Ola Normann",
            avsenderNavn = "Arbeidsgiver",
            orgNavn = "Norge AS",
            inntekt = inntekt,
            forespoersel = forespoersel,
            feil = emptyMap()
        )
            .toJson(HentForespoerselResultat.serializer())
    )
        .toJson(ResultJson.serializer())

    val resultatOkMedForrigeInntektJson = ResultJson(
        success = HentForespoerselResultat(
            sykmeldtNavn = "Ola Normann",
            avsenderNavn = "Arbeidsgiver",
            orgNavn = "Norge AS",
            inntekt = inntekt,
            forespoersel = forespoersel.copy(
                forespurtData = mockForespurtDataMedForrigeInntekt()
            ),
            feil = emptyMap()
        )
            .toJson(HentForespoerselResultat.serializer())
    )
        .toJson(ResultJson.serializer())

    fun responseJson(): String =
        """
        {
            "navn": "Ola Normann",
            "orgNavn": "Norge AS",
            "innsenderNavn": "Arbeidsgiver",
            "identitetsnummer": "${forespoersel.fnr}",
            "orgnrUnderenhet": "${forespoersel.orgnr}",
            "skjaeringstidspunkt": ${forespoersel.eksternBestemmendeFravaersdag().jsonStrOrNull()},
            "fravaersperioder": [${forespoersel.sykmeldingsperioder.joinToString(transform = Periode::hardcodedJson)}],
            "egenmeldingsperioder": [${forespoersel.egenmeldingsperioder.joinToString(transform = Periode::hardcodedJson)}],
            "bestemmendeFravaersdag": "${forespoersel.forslagBestemmendeFravaersdag()}",
            "eksternBestemmendeFravaersdag": ${forespoersel.eksternBestemmendeFravaersdag().jsonStrOrNull()},
            "bruttoinntekt": ${inntekt.gjennomsnitt()},
            "tidligereinntekter": [${inntekt.maanedOversikt.joinToString(transform = InntektPerMaaned::hardcodedJson)}],
            "behandlingsperiode": null,
            "behandlingsdager": [],
            "forespurtData": ${forespoersel.forespurtData.hardcodedJson()},
            "success": {
                "navn": "Ola Normann",
                "orgNavn": "Norge AS",
                "innsenderNavn": "Arbeidsgiver",
                "identitetsnummer": "${forespoersel.fnr}",
                "orgnrUnderenhet": "${forespoersel.orgnr}",
                "skjaeringstidspunkt": ${forespoersel.eksternBestemmendeFravaersdag().jsonStrOrNull()},
                "fravaersperioder": [${forespoersel.sykmeldingsperioder.joinToString(transform = Periode::hardcodedJson)}],
                "egenmeldingsperioder": [${forespoersel.egenmeldingsperioder.joinToString(transform = Periode::hardcodedJson)}],
                "bestemmendeFravaersdag": "${forespoersel.forslagBestemmendeFravaersdag()}",
                "eksternBestemmendeFravaersdag": ${forespoersel.eksternBestemmendeFravaersdag().jsonStrOrNull()},
                "bruttoinntekt": ${inntekt.gjennomsnitt()},
                "tidligereinntekter": [${inntekt.maanedOversikt.joinToString(transform = InntektPerMaaned::hardcodedJson)}],
                "behandlingsperiode": null,
                "behandlingsdager": [],
                "forespurtData": ${forespoersel.forespurtData.hardcodedJson()}
            }
        }
        """.removeJsonWhitespace()

    fun responseBareInntektJson(): String =
        """
        {
            "navn": "Ola Normann",
            "orgNavn": "Norge AS",
            "innsenderNavn": "Arbeidsgiver",
            "identitetsnummer": "${forespoersel.fnr}",
            "orgnrUnderenhet": "${forespoersel.orgnr}",
            "skjaeringstidspunkt": ${forespoersel.eksternBestemmendeFravaersdag().jsonStrOrNull()},
            "fravaersperioder": [${forespoersel.sykmeldingsperioder.joinToString(transform = Periode::hardcodedJson)}],
            "egenmeldingsperioder": [${forespoersel.egenmeldingsperioder.joinToString(transform = Periode::hardcodedJson)}],
            "bestemmendeFravaersdag": "${forespoersel.forslagBestemmendeFravaersdag()}",
            "eksternBestemmendeFravaersdag": ${forespoersel.eksternBestemmendeFravaersdag().jsonStrOrNull()},
            "bruttoinntekt": ${inntekt.gjennomsnitt()},
            "tidligereinntekter": [${inntekt.maanedOversikt.joinToString(transform = InntektPerMaaned::hardcodedJson)}],
            "behandlingsperiode": null,
            "behandlingsdager": [],
            "forespurtData": ${mockForespurtDataMedForrigeInntekt().hardcodedJson()},
            "success": {
                "navn": "Ola Normann",
                "orgNavn": "Norge AS",
                "innsenderNavn": "Arbeidsgiver",
                "identitetsnummer": "${forespoersel.fnr}",
                "orgnrUnderenhet": "${forespoersel.orgnr}",
                "skjaeringstidspunkt": ${forespoersel.eksternBestemmendeFravaersdag().jsonStrOrNull()},
                "fravaersperioder": [${forespoersel.sykmeldingsperioder.joinToString(transform = Periode::hardcodedJson)}],
                "egenmeldingsperioder": [${forespoersel.egenmeldingsperioder.joinToString(transform = Periode::hardcodedJson)}],
                "bestemmendeFravaersdag": "${forespoersel.forslagBestemmendeFravaersdag()}",
                "eksternBestemmendeFravaersdag": ${forespoersel.eksternBestemmendeFravaersdag().jsonStrOrNull()},
                "bruttoinntekt": ${inntekt.gjennomsnitt()},
                "tidligereinntekter": [${inntekt.maanedOversikt.joinToString(transform = InntektPerMaaned::hardcodedJson)}],
                "behandlingsperiode": null,
                "behandlingsdager": [],
                "forespurtData": ${mockForespurtDataMedForrigeInntekt().hardcodedJson()}
            }
        }
        """.removeJsonWhitespace()
}

private fun InntektPerMaaned.hardcodedJson(): String =
    """
    {
        "maaned": "$maaned",
        "inntekt": $inntekt
    }
    """

private fun ForespurtData.hardcodedJson(): String =
    """
    {
        "arbeidsgiverperiode": {
            "paakrevd": ${arbeidsgiverperiode.paakrevd}
        },
        "inntekt": {
            "paakrevd": ${inntekt.paakrevd},
            "forslag": ${inntekt.forslag.hardcodedJson()}
        },
        "refusjon": {
            "paakrevd": ${refusjon.paakrevd},
            "forslag": ${refusjon.forslag.hardcodedJson()}
        }
    }
    """

private fun ForslagInntekt.hardcodedJson(): String =
    when (this) {
        is ForslagInntekt.Grunnlag ->
            """
            {
                "type": "ForslagInntektGrunnlag",
                "forrigeInntekt": ${forrigeInntekt?.hardcodedJson()}
            }
            """

        is ForslagInntekt.Fastsatt ->
            """
            {
                "type": "ForslagInntektFastsatt",
                "fastsattInntekt": $fastsattInntekt
            }
            """
    }

private fun ForrigeInntekt.hardcodedJson(): String =
    """
    {
        "skjæringstidspunkt": "$skjæringstidspunkt",
        "kilde": "$kilde",
        "beløp": $beløp
    }
    """

private fun ForslagRefusjon.hardcodedJson(): String =
    """
    {
        "perioder": [${perioder.joinToString(transform = ForslagRefusjon.Periode::hardcodedJson)}],
        "opphoersdato": ${opphoersdato.jsonStrOrNull()}
    }
    """

private fun ForslagRefusjon.Periode.hardcodedJson(): String =
    """
    {
        "fom": "$fom",
        "beloep": $beloep
    }
    """
