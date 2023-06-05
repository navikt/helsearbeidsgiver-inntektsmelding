@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import com.fasterxml.jackson.module.kotlin.contains
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ForespoerselMottattIT : EndToEndTest() {

    private val FNR = "fnr-123"
    private val ORGNR = "orgnr-456"
    private val FORESPOERSEL = UUID.randomUUID().toString()

    @Test
    fun `skal ta imot forespørsel ny inntektsmelding, deretter opprette sak og oppgave`() {
        publish(
            mapOf(
                Pri.Key.NOTIS.str to Pri.NotisType.FORESPØRSEL_MOTTATT.name,
                Pri.Key.ORGNR.str to ORGNR,
                Pri.Key.FNR.str to FNR,
                Pri.Key.FORESPOERSEL_ID.str to FORESPOERSEL
            )
        )
        Thread.sleep(8000)

        with(filter(EventName.FORESPØRSEL_MOTTATT, BehovType.LAGRE_FORESPOERSEL).first()) {
            assertEquals(BehovType.LAGRE_FORESPOERSEL.name, get(Key.BEHOV.str).asText())

            assertEquals(EventName.FORESPØRSEL_MOTTATT.name, get(Key.EVENT_NAME.str).asText())
            assertEquals(ORGNR, get(Key.ORGNRUNDERENHET.str).asText())
            assertEquals(FNR, get(Key.IDENTITETSNUMMER.str).asText())
            assertEquals(FORESPOERSEL, get(Key.FORESPOERSEL_ID.str).asText())
        }

        with(filter(EventName.FORESPØRSEL_LAGRET).first()) {
            assertFalse(contains(Key.BEHOV.str))
            assertEquals(FNR, get(Key.IDENTITETSNUMMER.str).asText())
            assertEquals(ORGNR, get(Key.ORGNRUNDERENHET.str).asText())
            assertEquals(FORESPOERSEL, get(Key.FORESPOERSEL_ID.str).asText())
        }
    }
}
