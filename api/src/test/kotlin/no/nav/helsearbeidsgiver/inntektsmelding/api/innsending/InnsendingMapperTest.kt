@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import io.ktor.http.HttpStatusCode
import no.nav.helsearbeidsgiver.felles.Behov
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Løsning
import no.nav.helsearbeidsgiver.felles.Resultat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class InnsendingMapperTest {

    val løsningOk = Løsning(Behov.FULLT_NAVN.name, "abc")
    val løsningFeil = Løsning(Behov.FULLT_NAVN.name, error = Feilmelding("Oops!"))

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
        return InnsendingMapper("uuid", Resultat(løsninger))
    }
}
