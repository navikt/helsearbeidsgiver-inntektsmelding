package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import no.nav.helsearbeidsgiver.felles.ArbeidsforholdLøsning
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.HentTrengerImLøsning
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.InntektLøsning
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.TilgangskontrollLøsning
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.test.mock.MockUuid
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.buildResultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.ValidationResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertNotNull

private const val PATH = Routes.PREFIX + Routes.TRENGER

internal class TrengerRouteKtTest : ApiTest() {

    val objectMapper = customObjectMapper()
    val UUID = "abc-123"
    val GYLDIG_REQUEST = TrengerRequest(UUID)
    val UGYLDIG_REQUEST = TrengerRequest(" ")

    val RESULTAT_TRENGER_INNTEKT = Resultat(
        HENT_TRENGER_IM = HentTrengerImLøsning(TrengerInntekt("abc", "123", sykmeldingsperioder = emptyList(), forespurtData = emptyList())),
        INNTEKT = InntektLøsning(Inntekt(historisk = emptyList())),
        VIRKSOMHET = VirksomhetLøsning("Norge AS"),
        ARBEIDSFORHOLD = ArbeidsforholdLøsning(),
        FULLT_NAVN = NavnLøsning(PersonDato("Ola Normann", LocalDate.now()))
    )
    val RESULTAT_IKKE_TILGANG = Resultat(TILGANGSKONTROLL = TilgangskontrollLøsning(Tilgang.IKKE_TILGANG))
    val RESULTAT_HAR_TILGANG = Resultat(TILGANGSKONTROLL = TilgangskontrollLøsning(Tilgang.HAR_TILGANG))
    val RESULTAT_TILGANG_FEIL = Resultat(TILGANGSKONTROLL = TilgangskontrollLøsning(error = Feilmelding("feil", 500)))
    val RESULTAT_OK = buildResultat()

    @Test
    fun `skal returnere valideringsfeil ved ugyldig request`() = testApi {
        val response = post(PATH, UGYLDIG_REQUEST)
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNotNull(response.bodyAsText())
        val data: String = response.bodyAsText()
        val violations = objectMapper.readValue<ValidationResponse>(data).errors
        assertEquals(1, violations.size)
        assertEquals("uuid", violations[0].property)
    }

    @Test
    fun `skal returnere OK når trenger virker`() = testApi {
        coEvery {
            anyConstructed<RedisPoller>().getResultat(any(), any(), any())
        } returns RESULTAT_HAR_TILGANG andThenMany listOf(RESULTAT_TRENGER_INNTEKT, RESULTAT_OK)

        val response = post(PATH, GYLDIG_REQUEST)

        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `skal returnere Forbidden hvis feil ikke tilgang`() = testApi {
        coEvery {
            anyConstructed<RedisPoller>().getResultat(any(), any(), any())
        } returns RESULTAT_IKKE_TILGANG
        val response = post(PATH, GYLDIG_REQUEST)
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `skal returnere Forbidden hvis feil i Tilgangsresultet`() = testApi {
        coEvery {
            anyConstructed<RedisPoller>().getResultat(any(), any(), any())
        } returns RESULTAT_TILGANG_FEIL
        val response = post(PATH, GYLDIG_REQUEST)
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `skal returnere Internal server error hvis Redis timer ut`() = testApi {
        coEvery {
            anyConstructed<RedisPoller>().getResultat(any(), any(), any())
        } throws RedisPollerTimeoutException(MockUuid.STRING)
        val response = post(PATH, GYLDIG_REQUEST)
        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }
}
