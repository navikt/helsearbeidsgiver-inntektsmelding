package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.BegrunnelseIngenEllerRedusertUtbetalingKode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.lang.StringBuilder

class UtilsTest {

    @Test
    fun `ingen eller redusert refusjon begrunnelsetekst`() {
        BegrunnelseIngenEllerRedusertUtbetalingKode.entries.forEach {
            // sjekk at vi har lagt inn en fin tekst for alle koder:
            assertNotEquals(it.value, it.tekst(), "Mangler verdi i tekst()-funksjon!")
        }
        assertEquals("Lovlig fravær uten lønn", BegrunnelseIngenEllerRedusertUtbetalingKode.LOVLIG_FRAVAER.tekst())
    }

    @Test
    fun `del opp lange navn - behold as-is når det er kort tekst`() {
        val tekst = "Hei og hå"
        val liste = tekst.delOppLangeNavn()
        assertEquals(tekst, liste.first())
        assertEquals(1, liste.size)
    }

    @Test
    fun `del opp lange navn - del opp i jevne lengder hvis ingen mellomrom`() {
        val tekst = "HA"
        val tekstBuilder = StringBuilder()
        repeat(20) {
            tekstBuilder.append(tekst)
        }
        val liste = (tekstBuilder.toString() + tekstBuilder.toString() + tekst).delOppLangeNavn()
        assertEquals(tekstBuilder.toString(), liste.first())
        assertEquals(tekstBuilder.toString(), liste.get(1))
        assertEquals(tekst, liste.get(2))
    }

    @Test
    fun `del opp lange navn med mellomrom`() {
        val tekst = "Albert Fredriksens Saft- og Syltetøykokeri, avdeling Fredrikstad"
        val liste = tekst.delOppLangeNavn()
        assertEquals("Albert Fredriksens Saft- og", liste.first())
        assertEquals("Syltetøykokeri, avdeling Fredrikstad", liste.get(1))
    }
}
