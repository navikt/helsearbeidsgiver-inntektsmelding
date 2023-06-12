package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument.mockInntektsmeldingDokument
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MapOpprettJournalpostRequestKtTest {

    @Test
    fun mapOpprettJournalpostRequest() {
        val melding = mockInntektsmeldingDokument()
        val request = mapOpprettJournalpostRequest("abc", melding, "1234")
        assertTrue(request.dokumenter[0].dokumentVarianter[0].filtype == "XML")
    }
}
