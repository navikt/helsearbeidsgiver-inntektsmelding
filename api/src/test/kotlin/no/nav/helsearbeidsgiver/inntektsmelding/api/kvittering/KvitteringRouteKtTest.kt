package no.nav.helsearbeidsgiver.inntektsmelding.api.kvittering

import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.TilgangskontrollLøsning
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class KvitteringRouteKtTest : ApiTest() {

    private val UGYLDIG_UUID = "id_123"

    private val PATH = Routes.PREFIX + Routes.KVITTERING

    val RESULTAT_HAR_TILGANG = Resultat(TILGANGSKONTROLL = TilgangskontrollLøsning(Tilgang.HAR_TILGANG))
    val RESULTAT_OK =
        """{"orgnrUnderenhet":"123456789","identitetsnummer":"12345678901",
        "fulltNavn":"Ukjent","virksomhetNavn":"Ukjent","behandlingsdager":[],"egenmeldingsperioder":[],
        "bestemmendeFraværsdag":"2023-01-01","fraværsperioder":[{"fom":"2023-01-01","tom":"2023-01-31"}],
        "arbeidsgiverperioder":[{"fom":"2023-01-01","tom":"2023-01-16"}],
        "beregnetInntekt":3000,"fullLønnIArbeidsgiverPerioden":{"utbetalerFullLønn":true,"begrunnelse":null,"utbetalt":null},
        "refusjon":{"utbetalerHeleEllerDeler":false,"refusjonPrMnd":null,"refusjonOpphører":null,"refusjonEndringer":null},
        "naturalytelser":null,"tidspunkt":"2023-04-13T16:28:16.095083285+02:00","årsakInnsending":"Ny",
        "identitetsnummerInnsender":""}
        """.trimMargin()

    @Test
    fun `skal ikke godta tom uuid`() = testApi {
        val response = get(PATH + "?uuid=")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `skal ikke godta ugyldig uuid`() = testApi {
        val response = get(PATH + "?uuid=" + UGYLDIG_UUID)
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `skal godta gyldig uuid`() = testApi {
        coEvery {
            anyConstructed<RedisPoller>().getResultat(any(), any(), any())
        } returns RESULTAT_HAR_TILGANG
        coEvery {
            anyConstructed<RedisPoller>().getString(any(), any(), any())
        } returns RESULTAT_OK
        val response = get(PATH + "?uuid=" + UUID.randomUUID())
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
