@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkConstructor
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.test.mock.MockUuid
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.TestData
import no.nav.helsearbeidsgiver.inntektsmelding.api.authorization.DefaultAltinnAuthorizer
import no.nav.helsearbeidsgiver.inntektsmelding.api.mapper.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.ValidationResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class InnsendingRouteKtTest : ApiTest() {
    val objectMapper = customObjectMapper()

    private val FORESPØRSEL_ID = "id_123"
    private val PATH = Routes.PREFIX + Routes.INNSENDING + "/$FORESPØRSEL_ID"

    val GYLDIG_REQUEST = GYLDIG
    val UGYLDIG_REQUEST = GYLDIG.copy(
        identitetsnummer = TestData.notValidIdentitetsnummer,
        orgnrUnderenhet = TestData.notValidOrgNr
    )

    val RESULTAT_OK = Resultat(FULLT_NAVN = NavnLøsning("verdi"))
    val RESULTAT_FEIL = Resultat(FULLT_NAVN = NavnLøsning(error = Feilmelding("feil", 500)))

    @Test
    fun `skal godta og returnere kvittering`() = testApi {
        coEvery {
            anyConstructed<RedisPoller>().getResultat(any(), any(), any())
        } returns RESULTAT_OK

        mockkConstructor(DefaultAltinnAuthorizer::class)
        val ac = mockk<AltinnClient>()

        coEvery { anyConstructed<DefaultAltinnAuthorizer>().altinnClient } returns ac
        coEvery {
            anyConstructed<DefaultAltinnAuthorizer>().hasAccess(any(), any())
        } returns true

        val response = post(PATH, GYLDIG_REQUEST)

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(objectMapper.writeValueAsString(InnsendingResponse(FORESPØRSEL_ID)), response.bodyAsText())
    }

    @Test
    fun `skal returnere valideringsfeil ved ugyldig request`() = testApi {
        coEvery {
            anyConstructed<RedisPoller>().getResultat(any(), any(), any())
        } returns RESULTAT_FEIL

        mockkConstructor(DefaultAltinnAuthorizer::class)
        val ac = mockk<AltinnClient>()

        coEvery { anyConstructed<DefaultAltinnAuthorizer>().altinnClient } returns ac
        coEvery {
            anyConstructed<DefaultAltinnAuthorizer>().hasAccess(any(), any())
        } returns true

        val response = post(PATH, UGYLDIG_REQUEST)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNotNull(response.bodyAsText())
        val data: String = response.bodyAsText()
        val violations = objectMapper.readValue<ValidationResponse>(data).errors
        assertEquals(2, violations.size)
        assertEquals(Key.ORGNRUNDERENHET.str, violations[0].property)
        assertEquals("identitetsnummer", violations[1].property)
    }

    @Test
    fun `skal returnere feilmelding ved timeout fra Redis`() = testApi {
        coEvery {
            anyConstructed<RedisPoller>().getResultat(any(), any(), any())
        } throws RedisPollerTimeoutException(MockUuid.STRING)

        mockkConstructor(DefaultAltinnAuthorizer::class)
        val ac = mockk<AltinnClient>()

        coEvery { anyConstructed<DefaultAltinnAuthorizer>().altinnClient } returns ac
        coEvery {
            anyConstructed<DefaultAltinnAuthorizer>().hasAccess(any(), any())
        } returns true

        val response = post(PATH, GYLDIG_REQUEST)

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(objectMapper.writeValueAsString(RedisTimeoutResponse(FORESPØRSEL_ID, "Brukte for lang tid")), response.bodyAsText())
    }

    @Test
    fun `skal vise feil når et behov feiler`() = testApi {
        coEvery {
            anyConstructed<RedisPoller>().getResultat(any(), any(), any())
        } returns RESULTAT_FEIL

        mockkConstructor(DefaultAltinnAuthorizer::class)
        val ac = mockk<AltinnClient>()

        coEvery { anyConstructed<DefaultAltinnAuthorizer>().altinnClient } returns ac
        coEvery {
            anyConstructed<DefaultAltinnAuthorizer>().hasAccess(any(), any())
        } returns true

        val response = post(PATH, UGYLDIG_REQUEST)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNotNull(response.bodyAsText())
        val data: String = response.bodyAsText()
        val violations = objectMapper.readValue<ValidationResponse>(data).errors
        assertEquals(2, violations.size)
    }
}
