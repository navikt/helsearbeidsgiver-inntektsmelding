package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import no.nav.helsearbeidsgiver.felles.Feilmelding
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
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning
import no.nav.helsearbeidsgiver.felles.test.date.april
import no.nav.helsearbeidsgiver.felles.test.date.desember
import no.nav.helsearbeidsgiver.felles.test.date.februar
import no.nav.helsearbeidsgiver.felles.test.date.januar
import no.nav.helsearbeidsgiver.felles.test.date.mai
import no.nav.helsearbeidsgiver.felles.test.date.mars
import no.nav.helsearbeidsgiver.felles.test.mock.MockUuid
import no.nav.helsearbeidsgiver.felles.til
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.ValidationResponse
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.removeJsonWhitespace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

private const val PATH = Routes.PREFIX + Routes.TRENGER

class TrengerRouteKtTest : ApiTest() {

    @Test
    fun `skal returnere resultat og status CREATED når trenger virker`() = testApi {
        coEvery {
            anyConstructed<RedisPoller>().getResultat(any(), any(), any())
        } returns Mock.RESULTAT_HAR_TILGANG andThen Mock.RESULTAT_OK

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
                "forespurtData": [${mockTrengerInntekt.forespurtData.joinToString(transform = ForespurtData::hardcodedJson)}]
            }
        """.removeJsonWhitespace()
    }

    private fun trengerInntekt(): TrengerInntekt =
        TrengerInntekt(
            fnr = "abc",
            orgnr = "123",
            sykmeldingsperioder = listOf(
                1.april til 20.april,
                25.april til 30.april
            ),
            egenmeldingsperioder = listOf(
                29.mars til 29.mars,
                31.mars til 31.mars
            ),
            forespurtData = listOf(
                ForespurtData.ArbeidsgiverPeriode,
                ForespurtData.Inntekt(
                    forslag = ForslagInntekt(
                        beregningsmåneder = listOf(
                            februar(2022),
                            januar(2022),
                            desember(2022)
                        )
                    )
                ),
                ForespurtData.Refusjon(
                    forslag = listOf(
                        ForslagRefusjon(
                            fom = 1.april,
                            tom = null,
                            beløp = 2.0
                        )
                    )
                )
            )
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
    when (this) {
        is ForespurtData.ArbeidsgiverPeriode ->
            """{ "opplysningstype": "Arbeidsgiverperiode" }"""
        is ForespurtData.Inntekt ->
            """
            {
                "opplysningstype": "Inntekt",
                "forslag": {
                    "beregningsmåneder": [${forslag.beregningsmåneder.joinToString { yearMonth -> "\"$yearMonth\"" }}]
                }
            }
            """
        is ForespurtData.FastsattInntekt ->
            """
            {
                "opplysningstype": "FastsattInntekt",
                "fastsattInntekt": $fastsattInntekt
            }
            """
        is ForespurtData.Refusjon ->
            """
            {
                "opplysningstype": "Refusjon",
                "forslag": [${forslag.joinToString(transform = ForslagRefusjon::hardcodedJson)}]
            }
            """
    }

private fun Periode.hardcodedJson(): String =
    """
    {
        "fom": "$fom",
        "tom": "$tom"
    }
    """

private fun ForslagRefusjon.hardcodedJson(): String =
    """
    {
        "fom": "$fom",
        "tom": ${tom?.let { "\"$it\"" }},
        "beløp": $beløp
    }
    """
