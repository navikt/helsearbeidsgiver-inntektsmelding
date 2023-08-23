package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import com.fasterxml.jackson.databind.JsonNode
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import no.nav.helsearbeidsgiver.felles.json.toJsonNode
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.json.toDomeneMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.db.INNTEKTSMELDING_DOKUMENT
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class LagreJournalpostIdLøserTest {

    private val testRapid = TestRapid()
    private val BEHOV = BehovType.LAGRE_JOURNALPOST_ID
    private val inntektsmeldingRepo = mockk<InntektsmeldingRepository>()

    init {
        LagreJournalpostIdLøser(testRapid, inntektsmeldingRepo)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        clearAllMocks()
    }

    @Test
    fun `skal lagre journalpostId i databasen`() {
        coEvery { inntektsmeldingRepo.oppdaterJournapostId(any(), any()) } just Runs
        coEvery { inntektsmeldingRepo.hentNyeste(any()) } returns INNTEKTSMELDING_DOKUMENT

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(EventName.serializer()),
            Key.BEHOV to BEHOV.toJson(BehovType.serializer()),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.JOURNALPOST_ID to "123".toJson()
        )
        val message: JsonNode = journalpostLagretFraRapid(0) // Event sendes ut først, deretter løsning
        assertNotNull(message.path(Key.JOURNALPOST_ID.name).asText())
    }

    @Test
    fun `skal håndtere at journalpostId er null eller blank`() {
        coEvery { inntektsmeldingRepo.oppdaterJournapostId(any(), any()) } just Runs

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(EventName.serializer()),
            Key.BEHOV to BEHOV.toJson(BehovType.serializer()),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.JOURNALPOST_ID to "".toJson()
        )
        val feil = getFeil(0)
        assertNotNull(feil)
    }

    @Test
    fun `skal håndtere feil ved lagring`() {
        coEvery {
            inntektsmeldingRepo.oppdaterJournapostId(any(), any())
        } throws Exception()

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(EventName.serializer()),
            Key.BEHOV to BEHOV.toJson(BehovType.serializer()),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.JOURNALPOST_ID to "123".toJson()
        )
        val feilmelding = getFeil(0)
        assertNotNull(feilmelding)
    }

    private fun getFeil(index: Int) = testRapid.inspektør.message(index).toJsonElement().toJsonNode().toDomeneMessage<Fail>().feilmelding

    private fun journalpostLagretFraRapid(index: Int): JsonNode {
        val message = testRapid.inspektør.message(index)
        assertEquals(message.path(Key.EVENT_NAME.str).asText(), EventName.INNTEKTSMELDING_JOURNALFOERT.name)
        return message
    }
}
