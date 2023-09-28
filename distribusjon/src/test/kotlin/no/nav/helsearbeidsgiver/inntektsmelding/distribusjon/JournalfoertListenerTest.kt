package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.Jackson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JournalfoertListenerTest {

    private val rapid = TestRapid()
    private val JOURNALPOST_ID = "12345"

    init {
        JournalfoertListener(rapid)
    }

    @BeforeEach
    fun setup() {
        rapid.reset()
    }

    @Test
    fun `skal publisere behov om å distribuere dersom inntektsmelding blir journalført`() {
        rapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_JOURNALFOERT.toJson(),
            Key.JOURNALPOST_ID to JOURNALPOST_ID.toJson(),
            DataFelt.INNTEKTSMELDING_DOKUMENT to mockInntektsmeldingDokument().let(Jackson::toJson).parseJson()
        )
        val melding = rapid.inspektør.message(0)
        assertEquals(EventName.INNTEKTSMELDING_JOURNALFOERT.name, melding.get(Key.EVENT_NAME.str).asText(), "Skal sende riktig event")
        assertEquals(BehovType.DISTRIBUER_IM.name, melding.get(Key.BEHOV.str).asText(), "Skal be om riktig behov")
        assertEquals(JOURNALPOST_ID, melding.get(Key.JOURNALPOST_ID.str).asText(), "Påkrevd felt skal publiseres videre")
        assertNotNull(melding.get(DataFelt.INNTEKTSMELDING_DOKUMENT.str).asText(), "Påkrevd felt skal publiseres videre")
    }
}
