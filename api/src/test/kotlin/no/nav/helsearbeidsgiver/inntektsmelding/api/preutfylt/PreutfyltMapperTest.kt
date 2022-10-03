@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt

import io.ktor.http.HttpStatusCode
import no.nav.helsearbeidsgiver.felles.Behov
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Løsning
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.TestData
import no.nav.helsearbeidsgiver.inntektsmelding.api.dto.PreutfyltResponse
import org.junit.jupiter.api.Test
import org.valiktor.ConstraintViolationException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class PreutfyltMapperTest {

    val løsningOk = Løsning(Behov.FULLT_NAVN.name, "abc")
    val løsningOk2 = Løsning(Behov.VIRKSOMHET.name, "xyz")
    val løsningFeil = Løsning(Behov.FULLT_NAVN.name, error = Feilmelding("Oops"))

    @Test
    fun `skal kaste constraints exception når feil oppstår`() {
        val mapper = buildMapper(true, false)
        org.junit.jupiter.api.assertThrows<ConstraintViolationException> {
            mapper.getResponse()
        }
        val constraints = mapper.getConstraintViolations()
        assertEquals(1, constraints.size)
        assertEquals("identitetsnummer", constraints[0].property)
        assertEquals("Oops", constraints[0].value)
    }

    @Test
    fun `skal returnere feilinformasjon når feil oppstår`() {
        assertEquals(HttpStatusCode.InternalServerError, buildMapper(true, false).getStatus())
        assertTrue(buildMapper(true, false).hasErrors())
        assertEquals(HttpStatusCode.InternalServerError, buildMapper(false, false).getStatus())
        assertTrue(buildMapper(false, false).hasErrors())
    }

    @Test
    fun `skal returnere kvittering når det ikke er feil`() {
        assertEquals(HttpStatusCode.Created, buildMapper(true, true).getStatus())
        assertFalse(buildMapper(true, true).hasErrors())
        buildMapper(true, true).getResponse() as PreutfyltResponse
    }

    fun buildMapper(en: Boolean, to: Boolean): PreutfyltMapper {
        val løsninger = mutableListOf<Løsning>()
        løsninger.add(if (en) { løsningOk } else { løsningFeil })
        løsninger.add(if (to) { løsningOk2 } else { løsningFeil })
        val request = PreutfyllRequest(TestData.validOrgNr, TestData.validIdentitetsnummer)
        return PreutfyltMapper("uuid", Resultat(løsninger), request)
    }
}
