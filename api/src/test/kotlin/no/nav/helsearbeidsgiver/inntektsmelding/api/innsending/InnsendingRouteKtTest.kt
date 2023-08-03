@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.json.Jackson
import no.nav.helsearbeidsgiver.felles.test.mock.GYLDIG_INNSENDING_REQUEST
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPollerTimeoutException
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.TestData
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.JacksonErrorResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.response.RedisTimeoutResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.ValidationResponse
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertNotNull

class InnsendingRouteKtTest : ApiTest() {
    private val path = Routes.PREFIX + Routes.INNSENDING + "/${Mock.forespoerselId}"

    private val GYLDIG_REQUEST = GYLDIG_INNSENDING_REQUEST.let(Jackson::toJson).parseJson()
    private val UGYLDIG_REQUEST = GYLDIG_INNSENDING_REQUEST.copy(
        identitetsnummer = TestData.notValidIdentitetsnummer,
        orgnrUnderenhet = TestData.notValidOrgNr
    ).let(Jackson::toJson).parseJson()

    private val RESULTAT_OK = Resultat(FULLT_NAVN = NavnLøsning(PersonDato("verdi", LocalDate.now())))
    private val RESULTAT_FEIL = Resultat(FULLT_NAVN = NavnLøsning(error = Feilmelding("feil", 500)))

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `skal godta og returnere kvittering`() = testApi {
        mockTilgang(Tilgang.HAR_TILGANG)

        coEvery {
            anyConstructed<RedisPoller>().getResultat(any(), any(), any())
        } returns RESULTAT_OK

        val response = post(path, GYLDIG_REQUEST)
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(InnsendingResponse(Mock.forespoerselId.toString()).toJsonStr(InnsendingResponse.serializer()), response.bodyAsText())
    }

    @Test
    fun `skal returnere valideringsfeil ved ugyldig request`() = testApi {
        mockTilgang(Tilgang.HAR_TILGANG)

        coEvery {
            anyConstructed<RedisPoller>().getResultat(any(), any(), any())
        } returns RESULTAT_FEIL

        val response = post(path, UGYLDIG_REQUEST)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNotNull(response.bodyAsText())

        val violations = response.bodyAsText().fromJson(ValidationResponse.serializer()).errors

        assertEquals(2, violations.size)
        assertEquals(DataFelt.ORGNRUNDERENHET.str, violations[0].property)
        assertEquals("identitetsnummer", violations[1].property)
    }

    @Test
    fun `gir jackson-feil ved ugyldig request-json`() = testApi {
        val response = post(path, "\"ikke en request\"".toJson())

        val feilmelding = response.bodyAsText().fromJson(JacksonErrorResponse.serializer())

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(Mock.forespoerselId.toString(), feilmelding.forespoerselId)
        assertEquals("Feil under serialisering med jackson.", feilmelding.error)
    }

    @Test
    fun `skal returnere feilmelding ved timeout fra Redis`() = testApi {
        mockTilgang(Tilgang.HAR_TILGANG)

        coEvery {
            anyConstructed<RedisPoller>().getResultat(any(), any(), any())
        } throws RedisPollerTimeoutException(Mock.forespoerselId.toString())

        val response = post(path, GYLDIG_REQUEST)

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(RedisTimeoutResponse(Mock.forespoerselId.toString()).toJsonStr(RedisTimeoutResponse.serializer()), response.bodyAsText())
    }

    @Test
    fun `skal vise feil når et behov feiler`() = testApi {
        mockTilgang(Tilgang.HAR_TILGANG)

        coEvery {
            anyConstructed<RedisPoller>().getResultat(any(), any(), any())
        } returns RESULTAT_FEIL

        val response = post(path, UGYLDIG_REQUEST)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNotNull(response.bodyAsText())

        val violations = response.bodyAsText().fromJson(ValidationResponse.serializer()).errors

        assertEquals(2, violations.size)
    }

    private object Mock {
        val forespoerselId: UUID = UUID.randomUUID()
    }
}
