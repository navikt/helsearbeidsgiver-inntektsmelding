package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.mockk.coEvery
import no.nav.helsearbeidsgiver.dokarkiv.OpprettJournalpostResponse
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import java.util.UUID

class TrengerIT : EndToEndTest() {

    private val FNR = "fnr-123"
    private val ORGNR = "orgnr-456"
    private val FORESPØRSEL_ID = UUID.randomUUID().toString()

    private fun setup() {
        forespoerselRepository.lagreForespørsel(FORESPØRSEL_ID, ORGNR)

        coEvery {
            dokarkivClient.opprettJournalpost(any(), any(), any())
        } answers {
            OpprettJournalpostResponse(JOURNALPOST_ID, journalpostFerdigstilt = true, "FERDIGSTILT", "", emptyList())
        }
    }
    fun `Test trengerIM meldingsflyt`() {
    }
}
