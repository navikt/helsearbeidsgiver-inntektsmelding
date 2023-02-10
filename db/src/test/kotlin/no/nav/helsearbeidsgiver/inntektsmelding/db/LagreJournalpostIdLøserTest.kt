package no.nav.helsearbeidsgiver.inntektsmelding.db

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.JournalførtLøsning
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
    private val BEHOV = BehovType.JOURNALFOER.toString()
    private val repository = mockk<Repository>()

    init {
        løser = LagreJournalpostIdLøser(rapid, repository)
    }

    @Test
    fun `skal lagre journalpostId i databasen`() {
        coEvery {
            repository.oppdaterJournapostId(any(), any())
        } returns Unit
        val løsning = sendMelding(
            Key.BEHOV to listOf(BEHOV).toJson(String::toJson),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.LØSNING to Json.encodeToJsonElement(JournalpostLøsning("123"))
        )
        assertNotNull(løsning.value)
    }

    @Test
    fun `skal håndtere at journalpostId er null`() {
        coEvery {
            repository.oppdaterJournapostId(any(), any())
        } returns Unit
        val løsning = sendMelding(
            Key.BEHOV to listOf(BEHOV).toJson(String::toJson),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.LØSNING to Json.encodeToJsonElement(JournalpostLøsning(null))
        )
        assertNull(løsning.value)
        assertNotNull(løsning.error)
    }

    @Test
    fun `skal håndtere feil ved lagring`() {
        coEvery {
            repository.oppdaterJournapostId(any(), any())
        } throws Exception()
        val løsning = sendMelding(
            Key.BEHOV to listOf(BEHOV).toJson(String::toJson),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.LØSNING to Json.encodeToJsonElement(JournalpostLøsning("123"))
        )
        assertNull(løsning.value)
        assertNotNull(løsning.error)
    }

    private fun sendMelding(vararg melding: Pair<Key, JsonElement>): JournalførtLøsning {
        rapid.reset()
        rapid.sendJson(*melding.toList().toTypedArray())
        return rapid.inspektør.message(0).path(Key.LØSNING.str).get(BehovType.JOURNALFØRT_OK.name).toJsonElement().fromJson()
    }
}
