@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.mockk.coEvery
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.opprettNyOppgave
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.opprettNySak
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Disabled
internal class ForespoerselMottattIT : EndToEndTest() {

    val FNR = "fnr-123"
    val ORGNR = "orgnr-456"
    val FORESPOERSEL = UUID.randomUUID().toString()

    @Test
    @Disabled
    fun `skal ta imot forespørsel ny inntektsmelding, deretter opprette sak og oppgave`() {
        val arbeidsgiverNotifikasjonKlient = this.arbeidsgiverNotifikasjonKlient

        coEvery {
            arbeidsgiverNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any())
        } answers {
            "sak_id_123"
        }
        coEvery {
            arbeidsgiverNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any())
        } answers {
            "oppgave_id_456"
        }

        publish(
            mapOf(
                Pri.Key.NOTIS.str to Pri.NotisType.FORESPØRSEL_MOTTATT.name,
                Pri.Key.ORGNR.str to ORGNR,
                Pri.Key.FNR.str to FNR,
                Pri.Key.FORESPOERSEL_ID.str to FORESPOERSEL
            )
        )
        Thread.sleep(5000)
        /**
         * Motta forespørsel
         * Hent navn
         * Opprett NotifikasjonSak
         * PersisterSak
         * Opprett NotifikasjonOppgave
         * PersisterOppgave
         */
        assertEquals(5, getMessageCount())
        val msg1 = getMessage(0)
        assertEquals(EventName.FORESPØRSEL_MOTTATT.name, msg1.get(Key.EVENT_NAME.str).asText())
        assertEquals(BehovType.NOTIFIKASJON_TRENGER_IM.name, msg1.get(Key.BEHOV.str).asText())
        assertEquals(ORGNR, msg1.get(Key.ORGNRUNDERENHET.str).asText())
        assertEquals(FNR, msg1.get(Key.IDENTITETSNUMMER.str).asText())
        assertEquals(FORESPOERSEL, msg1.get(Key.UUID.str).asText())
    }
}
