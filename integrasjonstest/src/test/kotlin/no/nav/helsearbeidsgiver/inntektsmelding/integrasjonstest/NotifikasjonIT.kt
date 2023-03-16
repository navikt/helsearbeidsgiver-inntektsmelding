package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class NotifikasjonIT : Integrasjonstest() {

    @Test
    fun `skal lytte etter FORESPØRSEL_MOTTATT fra helsebro`() {
        val msg = mapOf(
            Key.EVENT_NAME.str to EventName.FORESPØRSEL_MOTTATT.name,
            Key.ORGNRUNDERENHET.str to "orgnr",
            Key.IDENTITETSNUMMER.str to "fnr",
            Key.UUID.str to "uuid"
        )
        publish(msg)
        Thread.sleep(5000)
        assertEquals(1, getResults().size)
    }
}
