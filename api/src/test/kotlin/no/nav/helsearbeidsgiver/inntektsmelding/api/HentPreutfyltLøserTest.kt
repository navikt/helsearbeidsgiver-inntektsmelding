package no.nav.helsearbeidsgiver.inntektsmelding.api

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.HentTrengerImLøsning
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

internal class HentPreutfyltLøserTest {

    private val rapid = TestRapid()
    private var løser: HentPreutfyltLøser
    private val BEHOV = BehovType.PREUTFYLL.toString()

    init {
        løser = HentPreutfyltLøser(rapid)
    }

    @Test
    fun `skal hente ut fnr og orgnr og publisere det samt neste behov`() {
        val resultat = sendMelding(
            mapOf(
                "@behov" to listOf(BEHOV),
                "@id" to UUID.randomUUID(),
                "@løsning" to mapOf(
                    BehovType.HENT_TRENGER_IM to HentTrengerImLøsning(
                        value = TrengerInntekt(
                            orgnr = "123",
                            fnr = "456",
                            sykemeldingsperioder = emptyList(),
                            egenmeldingsperioder = emptyList()
                        )
                    )
                )
            )
        )
        val nesteBehov: JsonNode = resultat.path("neste_behov")
        assertEquals(4, nesteBehov.size())
        val fnrNode: JsonNode = resultat.path(Key.IDENTITETSNUMMER.str)
        assertEquals("456", fnrNode.asText())
        val orgnrNode: JsonNode = resultat.path(Key.ORGNRUNDERENHET.str)
        assertEquals("123", orgnrNode.asText())
    }

    @Test
    fun `skal håndtere at det oppstod feil tidligere`() {
        val resultat = sendMelding(
            mapOf(
                "@behov" to listOf(BEHOV),
                "@id" to UUID.randomUUID(),
                "@løsning" to mapOf(
                    BehovType.HENT_TRENGER_IM to HentTrengerImLøsning(
                        error = Feilmelding("Feil")
                    )
                )
            )
        )
        val nesteBehov: JsonNode = resultat.path("neste_behov")
        assertEquals(0, nesteBehov.size())
        val fnrNode: JsonNode = resultat.path(Key.IDENTITETSNUMMER.str)
        assertEquals("", fnrNode.asText())
        val orgnrNode: JsonNode = resultat.path(Key.ORGNRUNDERENHET.str)
        assertEquals("", orgnrNode.asText())
    }

    private fun sendMelding(melding: Map<String, Any>): JsonNode {
        rapid.reset()
        rapid.sendTestMessage(customObjectMapper().writeValueAsString(melding))
        return rapid.inspektør.message(0)
    }
}
