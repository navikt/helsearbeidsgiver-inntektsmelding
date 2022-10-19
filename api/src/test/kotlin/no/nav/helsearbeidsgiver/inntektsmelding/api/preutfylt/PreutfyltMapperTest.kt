@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt

import io.ktor.http.HttpStatusCode
import no.nav.helsearbeidsgiver.felles.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.ArbeidsforholdLøsning
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.InntektLøsning
import no.nav.helsearbeidsgiver.felles.MottattHistoriskInntekt
import no.nav.helsearbeidsgiver.felles.MottattPeriode
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.felles.Syk
import no.nav.helsearbeidsgiver.felles.SykLøsning
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning
import no.nav.helsearbeidsgiver.inntektsmelding.api.TestData
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
    val løsningInntekt = InntektLøsning(Inntekt(250000.0, listOf(MottattHistoriskInntekt(YearMonth.now(), 32000.0))))
    val løsningArbeidsforhold = buildArbeidsforhold()
    val løsningSykdom = buildSykdom()
    val løsningFeil = NavnLøsning(error = Feilmelding("Feil"))

    fun buildArbeidsforhold(): ArbeidsforholdLøsning {
        val arbeidsforhold = listOf(
            Arbeidsforhold("af-1", "Norge AS", 80f),
            Arbeidsforhold("af-2", "Norge AS", 20f)
        )
        return ArbeidsforholdLøsning(arbeidsforhold)
    }

    fun buildSykdom(): SykLøsning {
        val fnr = TestData.validIdentitetsnummer
        val fra = LocalDate.of(2022, 10, 5)
        val fravaersperiode = mutableMapOf<String, List<MottattPeriode>>()
        fravaersperiode.put(fnr, listOf(MottattPeriode(fra, fra.plusDays(10))))
        val behandlingsperiode = MottattPeriode(fra, fra.plusDays(10))
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
        assertEquals("orgnrUnderenhet", constraints[0].property)
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
        val resultat = Resultat(
            FULLT_NAVN = if (en) { løsningNavn } else { løsningFeil },
            VIRKSOMHET = if (to) { løsningVirksomhet } else { VirksomhetLøsning(error = Feilmelding("Feil")) },
            ARBEIDSFORHOLD = løsningArbeidsforhold,
            SYK = løsningSykdom,
            INNTEKT = if (tre) { løsningInntekt } else { InntektLøsning(error = Feilmelding("Feil")) }
        )
        val request = PreutfyltRequest(TestData.validOrgNr, TestData.validIdentitetsnummer)
        return PreutfyltMapper("uuid", resultat, request)
    }
}
