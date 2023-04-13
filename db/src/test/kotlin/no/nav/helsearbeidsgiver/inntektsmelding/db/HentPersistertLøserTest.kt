package no.nav.helsearbeidsgiver.inntektsmelding.db

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.HentPersistertLøsning
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

internal class HentPersistertLøserTest {

    private val rapid = TestRapid()
    private var løser: HentPersistertLøser
    private val BEHOV = BehovType.HENT_PERSISTERT_IM.toString()
    private val repository = mockk<InntektsmeldingRepository>()

    init {
        løser = HentPersistertLøser(rapid, repository)
    }

    @Test
    fun `skal hente ut InntektsmeldingDokument`() {
        coEvery {
            repository.hentNyeste(any())
        } returns INNTEKTSMELDING_DOKUMENT
        val løsning = sendMelding(
            Key.BEHOV to listOf(BEHOV).toJson(String.serializer()),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.INITIATE_ID to UUID.randomUUID().toJson()
        )
        assertNotNull(løsning.value)
    }

    @Test
    fun `skal håndtere feil`() {
        coEvery {
            repository.hentNyeste(any())
        } throws Exception()
        val feilmelding = sendMeldingMedFeil(
            Key.BEHOV to listOf(BEHOV).toJson(String.serializer()),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.INITIATE_ID to UUID.randomUUID().toJson()
        )
        assertNotNull(feilmelding.melding)
        assertEquals("Klarte ikke hente persistert inntektsmelding", feilmelding.melding)
    }

    @Test
    fun `Ingen feilmelding dersom im ikke eksisterer`() {
        coEvery {
            repository.hentNyeste(any())
        } returns null
        val løsning = sendMelding(
            Key.BEHOV to listOf(BEHOV).toJson(String.serializer()),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.INITIATE_ID to UUID.randomUUID().toJson()
        )
        assertEquals("", løsning.value)
        assertNull(løsning.error)
    }

    private fun sendMelding(vararg melding: Pair<Key, JsonElement>): HentPersistertLøsning {
        rapid.reset()
        rapid.sendJson(*melding.toList().toTypedArray())
        return rapid.inspektør
            .message(0)
            .path(Key.LØSNING.str)
            .get(BehovType.HENT_PERSISTERT_IM.name)
            .toJsonElement()
            .fromJson(HentPersistertLøsning.serializer())
    }

    private fun sendMeldingMedFeil(vararg melding: Pair<Key, JsonElement>): Feilmelding {
        rapid.reset()
        rapid.sendJson(*melding.toList().toTypedArray())
        val json = rapid.inspektør
            .message(0)
            .path(Key.FAIL.str).asText()
        return customObjectMapper().readValue(json, Feilmelding::class.java)
        // TODO - serialisering med Feilmelding.serializer() funker ikke:
//            .toJsonElement()
//            .fromJson(Feilmelding.serializer())
    }
}
