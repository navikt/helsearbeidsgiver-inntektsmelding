package no.nav.helsearbeidsgiver.inntektsmelding.db

import com.fasterxml.jackson.databind.JsonNode
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.UUID

class LagreJournalpostIdLøserTest {

    private val rapid = TestRapid()
    private var løser: LagreJournalpostIdLøser
    private val BEHOV = BehovType.LAGRE_JOURNALPOST_ID
    private val inntektsmeldingRepo = mockk<InntektsmeldingRepository>()
    private val forespoerselRepo = mockk<ForespoerselRepository>()

    init {
        løser = LagreJournalpostIdLøser(rapid, inntektsmeldingRepo, forespoerselRepo)
    }

    @Test
    fun `skal lagre journalpostId i databasen`() {
        coEvery {
            inntektsmeldingRepo.hentNyeste(any())
        } returns INNTEKTSMELDING_DOKUMENT
        coEvery {
            inntektsmeldingRepo.oppdaterJournapostId(any(), any())
        } returns Unit
        coEvery {
            forespoerselRepo.hentOppgaveId(any())
        } returns "123"
        coEvery {
            forespoerselRepo.hentSakId(any())
        } returns "123"
        sendMelding(
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
        coEvery {
            inntektsmeldingRepo.oppdaterJournapostId(any(), any())
        } returns Unit
        sendMelding(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(EventName.serializer()),
            Key.BEHOV to BEHOV.toJson(BehovType.serializer()),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.JOURNALPOST_ID to "".toJson()
        )
        val feil: Feilmelding = getFeil(0)
        assertNotNull(feil)
    }

    @Test
    fun `skal håndtere feil ved lagring`() {
        coEvery {
            inntektsmeldingRepo.oppdaterJournapostId(any(), any())
        } throws Exception()

        sendMelding(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(EventName.serializer()),
            Key.BEHOV to BEHOV.toJson(BehovType.serializer()),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.JOURNALPOST_ID to "123".toJson()
        )
        val feilmelding: Feilmelding = getFeil(0)
        assertNotNull(feilmelding)
    }

    private fun sendMelding(vararg melding: Pair<Key, JsonElement>) {
        rapid.reset()
        rapid.sendJson(*melding.toList().toTypedArray())
    }

    private fun getFeil(index: Int) = rapid.inspektør.message(index).path(Key.FAIL.str).toJsonElement().fromJson(Feilmelding.serializer())

    private fun journalpostLagretFraRapid(index: Int): JsonNode {
        val message = rapid.inspektør.message(index)
        assertEquals(message.path(Key.EVENT_NAME.str).asText(), EventName.INNTEKTSMELDING_JOURNALFOERT.name)
        return message
    }
}
