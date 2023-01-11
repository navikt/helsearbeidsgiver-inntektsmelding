@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt

import io.ktor.http.HttpStatusCode
import no.nav.helsearbeidsgiver.felles.ArbeidsforholdLøsning
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.InntektLøsning
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.MottattHistoriskInntekt
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.Periode
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.felles.Syk
import no.nav.helsearbeidsgiver.felles.SykLøsning
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning
import no.nav.helsearbeidsgiver.inntektsmelding.api.TestData
import no.nav.helsearbeidsgiver.inntektsmelding.api.mockArbeidsforhold
import org.junit.jupiter.api.Test
import org.valiktor.ConstraintViolationException
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class PreutfyltMapperTest {

    val løsningNavn = NavnLøsning("abc")
    val løsningVirksomhet = VirksomhetLøsning("xyz")
    val løsningInntekt = InntektLøsning(Inntekt(listOf(MottattHistoriskInntekt(YearMonth.now(), 32_000.0))))
    val løsningArbeidsforhold = buildArbeidsforhold()
    val løsningSykdom = buildSykdom()
    val løsningFeil = NavnLøsning(error = Feilmelding("Feil"))

    fun buildArbeidsforhold(): ArbeidsforholdLøsning =
        mockArbeidsforhold()
            .let(::listOf)
            .let(::ArbeidsforholdLøsning)

    fun buildSykdom(): SykLøsning {
        val fra = LocalDate.of(2022, 10, 5)
        val fravaersperiode = listOf(Periode(fra, fra.plusDays(10)))
        val behandlingsperiode = Periode(fra, fra.plusDays(10))
        return SykLøsning(Syk(fravaersperiode = fravaersperiode, behandlingsperiode))
    }

    @Test
    fun `skal kaste constraints exception når feil oppstår`() {
        val mapper = buildMapper(true, false, false)
        org.junit.jupiter.api.assertThrows<ConstraintViolationException> {
            mapper.getResponse()
        }
        val constraints = mapper.getConstraintViolations()
        assertEquals(2, constraints.size)
        assertEquals(Key.ORGNRUNDERENHET.str, constraints[0].property)
        assertEquals("Feil", constraints[0].value)
    }

    @Test
    fun `skal returnere feilinformasjon når feil oppstår`() {
        assertEquals(HttpStatusCode.InternalServerError, buildMapper(true, false, false).getStatus())
        assertTrue(buildMapper(true, false, false).hasErrors())
        assertEquals(HttpStatusCode.InternalServerError, buildMapper(false, false, false).getStatus())
        assertTrue(buildMapper(false, false, false).hasErrors())
    }

    @Test
    fun `skal returnere kvittering når det ikke er feil`() {
        assertEquals(HttpStatusCode.Created, buildMapper(true, true, true).getStatus())
        assertFalse(buildMapper(true, true, true).hasErrors())
        buildMapper(true, true, true).getResponse()
    }

    fun buildMapper(en: Boolean, to: Boolean, tre: Boolean): PreutfyltMapper {
        val feilmelding = Feilmelding("Feil")

        val resultat = Resultat(
            FULLT_NAVN = if (en) løsningNavn else løsningFeil,
            VIRKSOMHET = if (to) løsningVirksomhet else VirksomhetLøsning(error = feilmelding),
            ARBEIDSFORHOLD = løsningArbeidsforhold,
            SYK = løsningSykdom,
            INNTEKT = if (tre) løsningInntekt else InntektLøsning(error = feilmelding)
        )
        val request = PreutfyltRequest(TestData.validOrgNr, TestData.validIdentitetsnummer)
        return PreutfyltMapper("uuid", resultat, request, emptyList(), emptyList())
    }
}
