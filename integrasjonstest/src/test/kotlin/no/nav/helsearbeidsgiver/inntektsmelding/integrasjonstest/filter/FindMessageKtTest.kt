package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.filter

import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class FindMessageKtTest : AbstractFilterBase() {

    @Test
    fun `skal finne message for event`() {
        assertEquals(1, findMessage(LISTE_MED_LØSNING, EVENT).size)
    }

    @Test
    fun `skal ikke finne message for event`() {
        assertEquals(0, findMessage(LISTE_MED_LØSNING, EventName.FORESPØRSEL_MOTTATT).size)
    }

    @Test
    fun `skal finne message for behov`() {
        assertEquals(1, findMessage(LISTE_MED_LØSNING, EVENT, BehovType.TILGANGSKONTROLL).size)
        assertEquals(1, findMessage(LISTE_MED_LØSNING, EVENT, BehovType.ARBEIDSGIVERE).size)
    }

    @Test
    fun `skal ikke finne message for behov`() {
        assertEquals(0, findMessage(LISTE_MED_LØSNING, EVENT, BehovType.HENT_IM_ORGNR).size)
    }

    @Test
    fun `skal finne message for løsning`() {
        assertEquals(1, findMessage(LISTE_MED_LØSNING, EVENT, BehovType.TILGANGSKONTROLL, true).size)
    }

    @Test
    fun `skal ikke finne message for løsning`() {
        assertEquals(1, findMessage(LISTE_MED_LØSNING, EVENT, BehovType.TILGANGSKONTROLL, false).size)
    }

    @Test
    fun `skal ikke finne message når løsning kreves`() {
        assertEquals(0, findMessage(LISTE_UTEN_LØSNING, EVENT, BehovType.TILGANGSKONTROLL, true).size)
    }
}
