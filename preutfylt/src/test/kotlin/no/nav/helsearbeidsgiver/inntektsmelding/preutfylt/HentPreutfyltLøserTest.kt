package no.nav.helsearbeidsgiver.inntektsmelding.preutfylt

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.HentTrengerImLøsning
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtDataListe
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

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
            Key.BEHOV to listOf(BEHOV).toJson(String.serializer()),
            Key.ID to UUID.randomUUID().toJson(),
            Key.SESSION to mapOf(
                BehovType.HENT_TRENGER_IM to HentTrengerImLøsning(
                    value = TrengerInntekt(
                        orgnr = "123",
                        fnr = "456",
                        sykmeldingsperioder = emptyList(),
                        forespurtData = mockForespurtDataListe()
                    )
                )
            )
                .toJson()
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
            Key.BEHOV to listOf(BEHOV).toJson(String.serializer()),
            Key.ID to UUID.randomUUID().toJson(),
            Key.SESSION to mapOf(
                BehovType.HENT_TRENGER_IM to HentTrengerImLøsning(
                    error = Feilmelding("Feil")
                )
            )
                .toJson()
        )
        val nesteBehov: JsonNode = resultat.path("neste_behov")
        assertEquals(0, nesteBehov.size())
        val fnrNode: JsonNode = resultat.path(Key.IDENTITETSNUMMER.str)
        assertEquals("", fnrNode.asText())
        val orgnrNode: JsonNode = resultat.path(Key.ORGNRUNDERENHET.str)
        assertEquals("", orgnrNode.asText())
    }

    private fun sendMelding(vararg melding: Pair<Key, JsonElement>): JsonNode {
        rapid.reset()
        rapid.sendJson(*melding.toList().toTypedArray())
        return rapid.inspektør.message(0)
    }
}

private fun Map<BehovType, HentTrengerImLøsning>.toJson(): JsonElement =
    toJson(
        MapSerializer(
            BehovType.serializer(),
            HentTrengerImLøsning.serializer()
        )
    )
