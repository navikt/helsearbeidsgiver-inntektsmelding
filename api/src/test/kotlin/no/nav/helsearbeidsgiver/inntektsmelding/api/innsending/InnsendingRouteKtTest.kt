@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.TilgangskontrollLøsning
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.parseJson
import no.nav.helsearbeidsgiver.felles.json.toJsonStr
import no.nav.helsearbeidsgiver.felles.test.mock.GYLDIG_INNSENDING_REQUEST
import no.nav.helsearbeidsgiver.felles.test.mock.MockUuid
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.TestData
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.ValidationResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertNotNull

private const val PATH = Routes.PREFIX + Routes.INNSENDING + "/${MockUuid.STRING}"

private val objectMapper = customObjectMapper()

// TODO gjør kompatibel med TestClient
class InnsendingRouteKtTest : ApiTest() {
    val GYLDIG_REQUEST = GYLDIG_INNSENDING_REQUEST
    val UGYLDIG_REQUEST = GYLDIG_INNSENDING_REQUEST.copy(
        identitetsnummer = TestData.notValidIdentitetsnummer,
        orgnrUnderenhet = TestData.notValidOrgNr
    )

    val RESULTAT_IKKE_TILGANG = Resultat(TILGANGSKONTROLL = TilgangskontrollLøsning(Tilgang.IKKE_TILGANG))
    val RESULTAT_HAR_TILGANG = Resultat(TILGANGSKONTROLL = TilgangskontrollLøsning(Tilgang.HAR_TILGANG))

    val RESULTAT_OK = Resultat(FULLT_NAVN = NavnLøsning(PersonDato("verdi", LocalDate.now())))
    val RESULTAT_FEIL = Resultat(FULLT_NAVN = NavnLøsning(error = Feilmelding("feil", 500)))

    @Test
    fun `skal godta og returnere kvittering`() = testApi {
        coEvery {
            anyConstructed<RedisPoller>().getResultat(any(), any(), any())
        } returns RESULTAT_HAR_TILGANG andThen RESULTAT_OK

        val response = post(PATH, GYLDIG_REQUEST.toJson())

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(InnsendingResponse(MockUuid.STRING).toJsonStr(InnsendingResponse.serializer()), response.bodyAsText())
    }

    @Test
    fun `skal returnere valideringsfeil ved ugyldig request`() = testApi {
        coEvery {
            anyConstructed<RedisPoller>().getResultat(any(), any(), any())
        } returns RESULTAT_HAR_TILGANG andThen RESULTAT_FEIL

        val response = post(PATH, UGYLDIG_REQUEST.toJson())

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNotNull(response.bodyAsText())

        val violations = response.bodyAsText().fromJson(ValidationResponse.serializer()).errors

        assertEquals(2, violations.size)
        assertEquals(Key.ORGNRUNDERENHET.str, violations[0].property)
        assertEquals("identitetsnummer", violations[1].property)
    }

    @Test
    fun `skal returnere feilmelding ved timeout fra Redis`() = testApi {
        coEvery {
            anyConstructed<RedisPoller>().getResultat(any(), any(), any())
        } throws RedisPollerTimeoutException(MockUuid.STRING)

        val response = post(PATH, GYLDIG_REQUEST.toJson())

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(RedisTimeoutResponse(MockUuid.STRING).toJsonStr(RedisTimeoutResponse.serializer()), response.bodyAsText())
    }

    @Test
    fun `skal vise feil når et behov feiler`() = testApi {
        coEvery {
            anyConstructed<RedisPoller>().getResultat(any(), any(), any())
        } returns RESULTAT_HAR_TILGANG andThen RESULTAT_FEIL

        val response = post(PATH, UGYLDIG_REQUEST.toJson())

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNotNull(response.bodyAsText())

        val violations = response.bodyAsText().fromJson(ValidationResponse.serializer()).errors

        assertEquals(2, violations.size)
    }
}

private fun InnsendingRequest.toJson(): JsonElement =
    objectMapper.writeValueAsString(this).parseJson()
