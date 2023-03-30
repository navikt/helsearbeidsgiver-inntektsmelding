package no.nav.helsearbeidsgiver.inntektsmelding.db

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.JournalpostLøsning
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

internal class LagreJournalpostIdLøserTest {

    private val rapid = TestRapid()
    private var løser: LagreJournalpostIdLøser
    private val BEHOV = BehovType.LAGRE_JOURNALPOST_ID
    private val repository = mockk<Repository>()

    init {
        løser = LagreJournalpostIdLøser(rapid, repository)
    }

    @Test
    fun `skal lagre journalpostId i databasen`() {
        coEvery {
            repository.hentNyeste(any())
        } returns INNTEKTSMELDING_DOKUMENT
        coEvery {
            repository.oppdaterJournapostId(any(), any())
        } returns Unit
        sendMelding(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(EventName.serializer()),
            Key.BEHOV to BEHOV.toJson(BehovType.serializer()),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.JOURNALPOST_ID to "123".toJson()
        )
        val løsning: JournalpostLøsning = journalpostLøsningFraRapid(0) // Event sendes ut først, deretter løsning
        assertNotNull(løsning.value)
    }

    @Test
    fun `skal håndtere at journalpostId er null eller blank`() {
        coEvery {
            repository.oppdaterJournapostId(any(), any())
        } returns Unit
        sendMelding(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(EventName.serializer()),
            Key.BEHOV to BEHOV.toJson(BehovType.serializer()),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.JOURNALPOST_ID to "".toJson()
        )
        val løsning: JournalpostLøsning = journalpostLøsningFraRapid(0)
        assertNull(løsning.value)
        assertNotNull(løsning.error)
    }

    @Test
    fun `skal håndtere feil ved lagring`() {
        coEvery {
            repository.oppdaterJournapostId(any(), any())
        } throws Exception()

        sendMelding(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(EventName.serializer()),
            Key.BEHOV to BEHOV.toJson(BehovType.serializer()),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.JOURNALPOST_ID to "123".toJson()
        )
        val løsning: JournalpostLøsning = journalpostLøsningFraRapid(0)
        assertNull(løsning.value)
        assertNotNull(løsning.error)
    }

    private fun sendMelding(vararg melding: Pair<Key, JsonElement>) {
        rapid.reset()
        rapid.sendJson(*melding.toList().toTypedArray())
    }

    private fun journalpostLøsningFraRapid(index: Int) =
        rapid.inspektør.message(index).path(Key.LØSNING.str).get(BehovType.LAGRE_JOURNALPOST_ID.name).toJsonElement().fromJson(
            JournalpostLøsning.serializer()
        )
}
