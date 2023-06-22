package no.nav.helsearbeidsgiver.inntektsmelding.db

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.contains
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class HentPersistertLøserTest {

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
        sendMelding(
            Key.BEHOV to listOf(BEHOV).toJson(String.serializer()),
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(EventName.serializer()),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.INITIATE_ID to UUID.randomUUID().toJson()
        )
        val melding = hentMelding(0)
        assertTrue(melding.contains(Key.DATA.str))
        assertTrue(melding.contains(DataFelt.INNTEKTSMELDING_DOKUMENT.str))
        assertTrue(
            customObjectMapper().readValue(melding.get(DataFelt.INNTEKTSMELDING_DOKUMENT.str).asText(), InntektsmeldingDokument::class.java) is InntektsmeldingDokument
        )
    }

    @Test
    fun `skal håndtere feil`() {
        coEvery {
            repository.hentNyeste(any())
        } throws Exception()
        val feilmelding = sendMeldingMedFeil(
            Key.BEHOV to listOf(BEHOV).toJson(String.serializer()),
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(EventName.serializer()),
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
        sendMelding(
            Key.BEHOV to listOf(BEHOV).toJson(String.serializer()),
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(EventName.serializer()),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.INITIATE_ID to UUID.randomUUID().toJson()
        )
        val message = hentMelding(0)
        assertTrue(message.contains(Key.DATA.str))
        assertEquals(message.get(DataFelt.INNTEKTSMELDING_DOKUMENT.str).asText(), "{}")
    }

    private fun sendMelding(vararg melding: Pair<Key, JsonElement>) {
        rapid.reset()
        rapid.sendJson(*melding.toList().toTypedArray())
    }

    private fun hentMelding(index: Int): JsonNode {
        return rapid.inspektør.message(index)
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
