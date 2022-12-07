@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import io.ktor.http.HttpStatusCode
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Løsning
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.valiktor.ConstraintViolationException

internal class InnsendingMapperTest {

    val løsningOk = NavnLøsning("abc")
    val løsningVirksomhet = VirksomhetLøsning("abc")
    val løsningVirksomhetFeil = VirksomhetLøsning(error = Feilmelding("Oops"))
    val løsningFeil = NavnLøsning(error = Feilmelding("Oops"))

    @Test
    fun `skal kaste constraints exception når feil oppstår`() {
        val mapper = buildMapper(true, false)
        assertThrows<ConstraintViolationException> {
            mapper.getResponse()
        }
        val constraints = mapper.getConstraintViolations()
        assertEquals(1, constraints.size)
        assertEquals(Key.ORGNRUNDERENHET.str, constraints[0].property)
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
    }

    fun buildMapper(en: Boolean, to: Boolean): InnsendingMapper {
        val løsninger = mutableListOf<Løsning>()
        løsninger.add(if (en) { løsningOk } else { løsningFeil })
        løsninger.add(if (to) { løsningOk } else { løsningFeil })
        return InnsendingMapper(
            "uuid",
            Resultat(
                FULLT_NAVN = if (en) { løsningOk } else { løsningFeil },
                VIRKSOMHET = if (to) { løsningVirksomhet } else { løsningVirksomhetFeil }
            )
        )
    }
}
