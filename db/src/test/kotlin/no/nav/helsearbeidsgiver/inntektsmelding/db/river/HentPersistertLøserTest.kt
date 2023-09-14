package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.contains
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.json.Jackson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.json.toDomeneMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.db.INNTEKTSMELDING_DOKUMENT
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
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
            repository.hentNyesteEksternEllerInternInntektsmelding(any())
        } returns Pair(INNTEKTSMELDING_DOKUMENT, null)
        sendMelding(
            Key.BEHOV to BEHOV.toJson(),
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.INITIATE_ID to UUID.randomUUID().toJson()
        )
        val melding = hentMelding(0)
        assertTrue(melding.contains(Key.DATA.str))
        assertTrue(melding.contains(DataFelt.INNTEKTSMELDING_DOKUMENT.str))
        assertDoesNotThrow {
            Jackson.fromJson<InntektsmeldingDokument>(melding.get(DataFelt.INNTEKTSMELDING_DOKUMENT.str).asText())
        }
    }

    @Test
    fun `skal håndtere feil`() {
        coEvery {
            repository.hentNyesteEksternEllerInternInntektsmelding(any())
        } throws Exception()
        val fail = sendMeldingMedFeil(
            Key.BEHOV to BEHOV.toJson(),
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.INITIATE_ID to UUID.randomUUID().toJson()
        )
        assertNotNull(fail.feilmelding)
        assertEquals("Klarte ikke hente persistert inntektsmelding", fail.feilmelding)
    }

    @Test
    fun `Ingen feilmelding dersom im ikke eksisterer`() {
        coEvery {
            repository.hentNyesteEksternEllerInternInntektsmelding(any())
        } returns null
        sendMelding(
            Key.BEHOV to BEHOV.toJson(),
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.INITIATE_ID to UUID.randomUUID().toJson()
        )
        val message = hentMelding(0)
        assertTrue(message.contains(Key.DATA.str))
        assertEquals("{}", message.get(DataFelt.INNTEKTSMELDING_DOKUMENT.str).asText())
    }

    private fun sendMelding(vararg melding: Pair<Key, JsonElement>) {
        rapid.reset()
        rapid.sendJson(*melding.toList().toTypedArray())
    }

    private fun hentMelding(index: Int): JsonNode {
        return rapid.inspektør.message(index)
    }

    private fun sendMeldingMedFeil(vararg melding: Pair<Key, JsonElement>): no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail {
        rapid.reset()
        rapid.sendJson(*melding.toList().toTypedArray())
        return rapid.firstMessage().toDomeneMessage()
        // TODO - serialisering med Feilmelding.serializer() funker ikke:
//            .toJsonElement()
//            .fromJson(Feilmelding.serializer())
    }
}
