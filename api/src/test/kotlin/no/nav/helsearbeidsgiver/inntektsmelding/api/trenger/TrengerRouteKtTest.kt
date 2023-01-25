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
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.buildResultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.ValidationResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
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
        FULLT_NAVN = NavnLøsning("Ola Normann")
    )
    val RESULTAT_FEIL = Resultat(HENT_TRENGER_IM = HentTrengerImLøsning(error = Feilmelding("feil", 500)))
    val RESULTAT_OK = buildResultat()

    @Test
    fun `skal returnere valideringsfeil ved ugyldig request`() = testApi {
        coEvery {
            anyConstructed<RedisPoller>().getResultat(any(), any(), any())
        } returns RESULTAT_FEIL

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
        } returnsMany listOf(RESULTAT_TRENGER_INNTEKT, RESULTAT_OK)

        val response = post(PATH, GYLDIG_REQUEST)

        assertEquals(HttpStatusCode.Created, response.status)
    }
}
