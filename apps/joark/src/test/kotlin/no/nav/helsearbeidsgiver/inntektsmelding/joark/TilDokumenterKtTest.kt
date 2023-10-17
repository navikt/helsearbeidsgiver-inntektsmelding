package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmelding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TilDokumenterKtTest {

    @Test
    fun tilDokumenter() {
        val mockInntektsmelding = mockInntektsmelding()

        val dokumenter = tilDokumenter("uuid-hoppende-slott", mockInntektsmelding)

        assertEquals(1, dokumenter.size)
        assertEquals(2, dokumenter[0].dokumentVarianter.size)
        assertEquals("XML", dokumenter[0].dokumentVarianter[0].filtype)
        assertEquals("PDFA", dokumenter[0].dokumentVarianter[1].filtype)
    }
}
