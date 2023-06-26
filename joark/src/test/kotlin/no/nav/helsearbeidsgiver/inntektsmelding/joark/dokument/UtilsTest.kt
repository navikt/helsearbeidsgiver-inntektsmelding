package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.BegrunnelseIngenEllerRedusertUtbetalingKode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.lang.StringBuilder

class UtilsTest {

    @Test
    fun `ingen eller redusert refusjon begrunnelsetekst`() {
        val begrunnelser = BegrunnelseIngenEllerRedusertUtbetalingKode.values()
        begrunnelser.forEach {
            // sjekk at vi har lagt inn en fin tekst for alle koder:
            assertNotEquals(it.value, it.tekst(), "Mangler verdi i tekst()-funksjon!")
        }
        assertEquals("Lovlig fravær uten lønn", BegrunnelseIngenEllerRedusertUtbetalingKode.LOVLIG_FRAVAER.tekst())
    }

    @Test
    fun `del opp lange navn - behold as-is når det er kort tekst`() {
        val tekst = "Hei og hå"
        val liste = delOppLangeNavn(tekst)
        assertEquals(tekst, liste.first())
    }

    @Test
    fun `del opp lange navn - del opp i jevne lengder hvis ingen mellomrom`() {
        val tekst = "HA"
        val tekstBuilder = StringBuilder()
        repeat(20) {
            tekstBuilder.append(tekst)
        }
        val liste = delOppLangeNavn(tekstBuilder.toString() + tekstBuilder.toString() + tekst)
        assertEquals(tekstBuilder.toString(), liste.first())
        assertEquals(tekstBuilder.toString(), liste.get(1))
        assertEquals(tekst, liste.get(2))
    }

    @Test
    fun `del opp lange navn med mellomrom`() {
        val tekst = "Albert Fredriksens Saft- og Syltetøykokeri, avdeling Fredrikstad"
        val liste = delOppLangeNavn(tekst)
        liste.forEach{ println(it)}
        assertEquals("Albert Fredriksens Saft- og", liste.first())
        assertEquals("Syltetøykokeri, avdeling Fredrikstad", liste.get(1))
    }
}
