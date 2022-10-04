@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt

import io.ktor.http.HttpStatusCode
import no.nav.helsearbeidsgiver.felles.Behov
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Løsning
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.TestData
import no.nav.helsearbeidsgiver.inntektsmelding.api.dto.Inntekt
import no.nav.helsearbeidsgiver.inntektsmelding.api.dto.MottattHistoriskInntekt
import org.junit.jupiter.api.Test
import org.valiktor.ConstraintViolationException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class PreutfyltMapperTest {

    val løsningNavn = Løsning(Behov.FULLT_NAVN.name, "abc")
    val løsningVirksomhet = Løsning(Behov.VIRKSOMHET.name, "xyz")
    val løsningInntekt = Løsning(Behov.INNTEKT.name, Inntekt(250000, listOf(MottattHistoriskInntekt("Januar", 25000))))
    val løsningArbeidsforhold = Løsning(Behov.ARBEIDSFORHOLD.name, "arbeidsforhold")
    val løsningFeil = Løsning(Behov.FULLT_NAVN.name, error = Feilmelding("Oops"))

    @Test
    fun `skal kaste constraints exception når feil oppstår`() {
        val mapper = buildMapper(true, false, false)
        org.junit.jupiter.api.assertThrows<ConstraintViolationException> {
            mapper.getResponse()
        }
        val constraints = mapper.getConstraintViolations()
        assertEquals(2, constraints.size)
        assertEquals("identitetsnummer", constraints[0].property)
        assertEquals("Oops", constraints[0].value)
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
        val løsninger = mutableListOf<Løsning>()
        løsninger.add(if (en) { løsningNavn } else { løsningFeil })
        løsninger.add(if (to) { løsningVirksomhet } else { løsningFeil })
        løsninger.add(if (tre) { løsningInntekt } else { løsningFeil })
        løsninger.add(løsningArbeidsforhold)
        val request = PreutfyllRequest(TestData.validOrgNr, TestData.validIdentitetsnummer)
        return PreutfyltMapper("uuid", Resultat(løsninger), request)
    }
}
