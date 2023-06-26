package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.BegrunnelseIngenEllerRedusertUtbetalingKode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class UtilsTest {

    @Test
    fun `ingen eller redusert refusjon begrunnelsetekst`() {
        val begrunnelser = BegrunnelseIngenEllerRedusertUtbetalingKode.values()
        begrunnelser.forEach {
            // sjekk at vi har lagt inn en fin tekst for alle koder:
            assertNotEquals(it.value, it.tekst(), "Mangler verdi i tekst()-funksjon!")
        }
        assertEquals("Lovlig fravær uten lønn", BegrunnelseIngenEllerRedusertUtbetalingKode.LOVLIG_FRAVAER.tekst())
    }
}
