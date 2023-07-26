package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.ForespoerselStatus
import no.nav.helsearbeidsgiver.felles.ForespoerselType
import no.nav.helsearbeidsgiver.felles.ForespurtData
import no.nav.helsearbeidsgiver.felles.ForslagInntekt
import no.nav.helsearbeidsgiver.felles.ForslagRefusjon
import no.nav.helsearbeidsgiver.felles.HentTrengerImLøsning
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.InntektLøsning
import no.nav.helsearbeidsgiver.felles.MottattHistoriskInntekt
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.Periode
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.TilgangskontrollLøsning
import no.nav.helsearbeidsgiver.felles.TrengerData
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning
import no.nav.helsearbeidsgiver.felles.test.mock.MockUuid
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtData
import no.nav.helsearbeidsgiver.felles.til
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.ValidationResponse
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.removeJsonWhitespace
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.desember
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.mai
import no.nav.helsearbeidsgiver.utils.test.date.mars
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

private const val PATH = Routes.PREFIX + Routes.TRENGER

class TrengerRouteKtTest : ApiTest() {

    @Test
    fun `skal returnere resultat og status CREATED når trenger virker`() = testApi {
        coEvery {
            anyConstructed<RedisPoller>().getResultat(any(), any(), any())
        } returns Mock.RESULTAT_HAR_TILGANG

        coEvery {
            anyConstructed<RedisPoller>().getString(any(), any(), any())
        } returns Mock.TRENGER_DATA_OK.toJsonStr(TrengerData.serializer())

        val expectedJson = Mock.trengerResponseJson()

        val response = post(PATH, Mock.GYLDIG_REQUEST, TrengerRequest.serializer())

        val actualJson = response.bodyAsText()

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `skal returnere valideringsfeil ved ugyldig request`() = testApi {
        val response = post(PATH, Mock.UGYLDIG_REQUEST, TrengerRequest.serializer())
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNotNull(response.bodyAsText())

        val violations = response.bodyAsText().fromJson(ValidationResponse.serializer()).errors

        assertEquals(1, violations.size)
        assertEquals("uuid", violations[0].property)
    }

    @Test
    fun `skal returnere Forbidden hvis feil ikke tilgang`() = testApi {
        coEvery {
            anyConstructed<RedisPoller>().getResultat(any(), any(), any())
        } returns Mock.RESULTAT_IKKE_TILGANG
        val response = post(PATH, Mock.GYLDIG_REQUEST, TrengerRequest.serializer())
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `skal returnere Forbidden hvis feil i Tilgangsresultet`() = testApi {
        coEvery {
            anyConstructed<RedisPoller>().getResultat(any(), any(), any())
        } returns Mock.RESULTAT_TILGANG_FEIL
        val response = post(PATH, Mock.GYLDIG_REQUEST, TrengerRequest.serializer())
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `skal returnere Internal server error hvis Redis timer ut`() = testApi {
        coEvery {
            anyConstructed<RedisPoller>().getResultat(any(), any(), any())
        } throws RedisPollerTimeoutException(MockUuid.STRING)
        val response = post(PATH, Mock.GYLDIG_REQUEST, TrengerRequest.serializer())
        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }
}

private object Mock {
    const val UUID = "abc-123"
    val GYLDIG_REQUEST = TrengerRequest(UUID)
    val UGYLDIG_REQUEST = TrengerRequest(" ")

    val RESULTAT_OK = Resultat(
        HENT_TRENGER_IM = HentTrengerImLøsning(
            value = trengerInntekt()
        ),
        FULLT_NAVN = NavnLøsning(PersonDato("Ola Normann", 1.mai)),
        VIRKSOMHET = VirksomhetLøsning("Norge AS"),
        INNTEKT = InntektLøsning(
            value = inntekt()
        )
    )
    val RESULTAT_IKKE_TILGANG = Resultat(TILGANGSKONTROLL = TilgangskontrollLøsning(Tilgang.IKKE_TILGANG))
    val RESULTAT_HAR_TILGANG = Resultat(TILGANGSKONTROLL = TilgangskontrollLøsning(Tilgang.HAR_TILGANG))
    val RESULTAT_TILGANG_FEIL = Resultat(TILGANGSKONTROLL = TilgangskontrollLøsning(error = Feilmelding("feil", 500)))

    val TRENGER_DATA_OK = TrengerData(
        fnr = trengerInntekt().fnr,
        orgnr = trengerInntekt().orgnr,
        personDato = PersonDato("Ola Normann", 1.mai),
        virksomhetNavn = "Norge AS",
        inntekt = inntekt(),
        fravarsPerioder = trengerInntekt().sykmeldingsperioder,
        egenmeldingsPerioder = trengerInntekt().egenmeldingsperioder,
        forespurtData = trengerInntekt().forespurtData,
        bruttoinntekt = inntekt().gjennomsnitt(),
        tidligereinntekter = inntekt().historisk
    )

    fun trengerResponseJson(): String {
        val mockTrengerInntekt = trengerInntekt()
        val mockInntekt = inntekt()
        return """
            {
                "navn": "${RESULTAT_OK.FULLT_NAVN?.value?.navn}",
                "orgNavn": "${RESULTAT_OK.VIRKSOMHET?.value}",
                "identitetsnummer": "${mockTrengerInntekt.fnr}",
                "orgnrUnderenhet": "${mockTrengerInntekt.orgnr}",
                "fravaersperioder": [${mockTrengerInntekt.sykmeldingsperioder.joinToString(transform = Periode::hardcodedJson)}],
                "egenmeldingsperioder": [${mockTrengerInntekt.egenmeldingsperioder.joinToString(transform = Periode::hardcodedJson)}],
                "bruttoinntekt": ${mockInntekt.gjennomsnitt()},
                "tidligereinntekter": [${mockInntekt.historisk.joinToString(transform = MottattHistoriskInntekt::hardcodedJson)}],
                "behandlingsperiode": null,
                "behandlingsdager": [],
                "forespurtData": ${mockTrengerInntekt.forespurtData.hardcodedJson()}
            }
        """.removeJsonWhitespace()
    }

    private fun trengerInntekt(): TrengerInntekt =
        TrengerInntekt(
            type = ForespoerselType.KOMPLETT,
            status = ForespoerselStatus.AKTIV,
            fnr = "abc",
            orgnr = "123",
            skjaeringstidspunkt = 11.januar(2018),
            sykmeldingsperioder = listOf(
                1.april til 20.april,
                25.april til 30.april
            ),
            egenmeldingsperioder = listOf(
                29.mars til 29.mars,
                31.mars til 31.mars
            ),
            forespurtData = mockForespurtData()
        )

    private fun inntekt(): Inntekt =
        Inntekt(
            listOf(
                MottattHistoriskInntekt(
                    maaned = februar(2022),
                    inntekt = 2.0
                ),
                MottattHistoriskInntekt(
                    maaned = januar(2022),
                    inntekt = 1.0
                ),
                MottattHistoriskInntekt(
                    maaned = desember(2022),
                    inntekt = 3.0
                )
            )
        )
}

private fun MottattHistoriskInntekt.hardcodedJson(): String =
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
                "beregningsmaaneder": [${beregningsmaaneder.joinToString { yearMonth -> "\"$yearMonth\"" }}]
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

private fun Periode.hardcodedJson(): String =
    """
    {
        "fom": "$fom",
        "tom": "$tom"
    }
    """

private fun <T : Any> T?.jsonStrOrNull(): String? =
    this?.let { "\"$it\"" }
