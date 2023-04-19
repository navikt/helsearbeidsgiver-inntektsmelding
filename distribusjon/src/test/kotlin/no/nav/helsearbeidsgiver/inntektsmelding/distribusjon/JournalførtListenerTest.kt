package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class JournalførtListenerTest {

    private val rapid = TestRapid()
    private val om = customObjectMapper()
    private val JOURNALPOST_ID = "12345"

    init {
        JournalførtListener(rapid)
    }

    @Test
    fun `skal publisere behov om å distribuere dersom inntektsmelding blir journalført`() {
        sendMelding(
            mapOf(
                Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_JOURNALFOERT,
                Key.JOURNALPOST_ID.str to JOURNALPOST_ID,
                Key.INNTEKTSMELDING_DOKUMENT.str to MockInntektsmeldingDokument()
            )
        )
        val melding = rapid.inspektør.message(0)
        assertEquals(EventName.INNTEKTSMELDING_JOURNALFOERT.name, melding.get(Key.EVENT_NAME.str).asText(), "Skal sende riktig event")
        assertEquals(BehovType.DISTRIBUER_IM.name, melding.get(Key.BEHOV.str).asText(), "Skal be om riktig behov")
        assertEquals(JOURNALPOST_ID, melding.get(Key.JOURNALPOST_ID.str).asText(), "Påkrevd felt skal publiseres videre")
        assertNotNull(melding.get(Key.INNTEKTSMELDING_DOKUMENT.str).asText(), "Påkrevd felt skal publiseres videre")
    }

    private fun sendMelding(melding: Map<String, Any>) {
        rapid.reset()
        rapid.sendTestMessage(om.writeValueAsString(melding))
    }
}
