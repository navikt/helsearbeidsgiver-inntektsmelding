package no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.felles.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.ArbeidsforholdLøsning
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.InntektLøsning
import no.nav.helsearbeidsgiver.felles.MottattHistoriskInntekt
import no.nav.helsearbeidsgiver.felles.MottattPeriode
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.felles.Syk
import no.nav.helsearbeidsgiver.felles.SykLøsning
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning
import no.nav.helsearbeidsgiver.inntektsmelding.api.RedisPoller
import no.nav.helsearbeidsgiver.inntektsmelding.api.TestData
import no.nav.helsearbeidsgiver.inntektsmelding.api.buildObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class PreutfyllingRouteKtTest {

    val producer = mockk<PreutfyltProducer>()
    val poller = mockk<RedisPoller>()
    val objectMapper = buildObjectMapper()
    val GYLDIG_REQUEST = PreutfyllRequest(TestData.validOrgNr, TestData.validIdentitetsnummer)

    private val UUID = "abc-123"
    private val RESULTAT_OK = buildResultat()

    fun buildResultat(): Resultat {
        val arbeidsforhold = listOf(
            Arbeidsforhold("af-1", "Norge AS", 80f),
            Arbeidsforhold("af-2", "Norge AS", 20f)
        )
        val fra = LocalDate.of(2022, 10, 5)
        val fravaersperiode = mutableMapOf<String, List<MottattPeriode>>()
        fravaersperiode.put(TestData.validIdentitetsnummer, listOf(MottattPeriode(fra, fra.plusDays(10))))
        val behandlingsperiode = MottattPeriode(fra, fra.plusDays(10))
        return Resultat(
            FULLT_NAVN = NavnLøsning("Navn Navnesen"),
            SYK = SykLøsning(Syk(fravaersperiode, behandlingsperiode)),
            VIRKSOMHET = VirksomhetLøsning("Virksomhet AS"),
            ARBEIDSFORHOLD = ArbeidsforholdLøsning(arbeidsforhold),
            INNTEKT = InntektLøsning(Inntekt(300L, listOf(MottattHistoriskInntekt("Januar", 32000))))
        )
    }

    @Test
    fun `skal godta og returnere kvittering`() = testApplication {
        every {
            producer.publish(any())
        } returns UUID
        every {
            runBlocking {
                poller.getValue(any(), any(), any())
            }
        } returns objectMapper.writeValueAsString(RESULTAT_OK)
        // assertEquals("", objectMapper.writeValueAsString(RESULTAT_OK))
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }
        application {
            install(ContentNegotiation) {
                jackson()
            }
            routing {
                preutfyltRoute(producer, poller, objectMapper)
            }
        }
        val response = client.post("/preutfyll") {
            contentType(ContentType.Application.Json)
            setBody(GYLDIG_REQUEST)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        // assertEquals(objectMapper.writeValueAsString(PreutfyltResponse(UUID)), response.bodyAsText())
    }
}
