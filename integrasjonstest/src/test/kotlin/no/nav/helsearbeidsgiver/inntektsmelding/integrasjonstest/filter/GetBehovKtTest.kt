package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.filter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class GetBehovKtTest : AbstractFilterBase() {

    @Test
    fun `skal finne behov når null`() {
        assertEquals(0, toNode(BEHOV_NULL).getBehov().size)
    }

    @Test
    fun `skal finne behov når uten`() {
        assertEquals(0, toNode(BEHOV_UTEN).getBehov().size)
    }

    @Test
    fun `skal finne behov når enkel`() {
        assertEquals(1, toNode(BEHOV_ENKEL).getBehov().size)
    }

    @Test
    fun `skal finne behov når dobbel`() {
        assertEquals(2, toNode(BEHOV_LISTE).getBehov().size)
    }
}
