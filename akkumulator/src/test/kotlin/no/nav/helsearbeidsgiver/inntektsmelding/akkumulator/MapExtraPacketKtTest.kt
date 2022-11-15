package no.nav.helsearbeidsgiver.inntektsmelding.akkumulator

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class MapExtraPacketKtTest {

    private val FULLT_NAVN_OK = NavnLøsning(value = "xyz")
    private val objectMapper = ObjectMapper()

    @Test
    fun `skal håndtere at det ikke er ekstra behov`() {
        val resultat = sendMelding(
            mapOf(
                "uuid" to "uuid",
                "@behov" to listOf(BehovType.FULLT_NAVN, BehovType.INNTEKT),
                "@løsning" to mapOf(
                    BehovType.FULLT_NAVN.toString() to FULLT_NAVN_OK
                ),
                "extra" to emptyList<String>()
            )
        )
        val behov = resultat[Key.BEHOV.str]
        assertEquals(2, behov.size(), "Skal beholde behov uforandret")
        assertEquals(BehovType.FULLT_NAVN.toString(), behov[0].asText())
        assertEquals(BehovType.INNTEKT.toString(), behov[1].asText())
        val extra = resultat.get("extra")
        assertEquals(0, extra.size(), "Extra skal bli tom")
    }

    @Test
    fun `skal håndtere at det er et ekstra behov uten pause`() {
        val resultat = sendMelding(
            mapOf(
                "uuid" to "uuid",
                "@behov" to listOf(BehovType.FULLT_NAVN.toString(), BehovType.INNTEKT.toString()),
                "@løsning" to mapOf(
                    BehovType.FULLT_NAVN.toString() to FULLT_NAVN_OK
                ),
                "extra" to listOf(BehovType.VIRKSOMHET)
            )
        )
        val behov = resultat[Key.BEHOV.str]
        assertEquals(3, behov.size(), "Skal beholde behov uforandret")
        assertEquals(BehovType.FULLT_NAVN.toString(), behov[0].asText())
        assertEquals(BehovType.INNTEKT.toString(), behov[1].asText())
        assertEquals(BehovType.VIRKSOMHET.toString(), behov[2].asText())
        val extra = resultat.get("extra")
        assertEquals(0, extra.size(), "Extra skal bli tom")
    }

    @Test
    fun `skal håndtere at det er flere ekstra behov med pause`() {
        val resultat = sendMelding(
            mapOf(
                "@behov" to listOf(BehovType.FULLT_NAVN.toString(), BehovType.INNTEKT.toString()),
                "extra" to listOf(BehovType.VIRKSOMHET, BehovType.PAUSE, BehovType.IM_VALIDERING, BehovType.PAUSE, BehovType.JOURNALFOER)
            )
        )
        val behov = resultat[Key.BEHOV.str]
        assertEquals(3, behov.size(), "Skal beholde behov uforandret")
        assertEquals(BehovType.FULLT_NAVN.toString(), behov[0].asText())
        assertEquals(BehovType.INNTEKT.toString(), behov[1].asText())
        assertEquals(BehovType.VIRKSOMHET.toString(), behov[2].asText())
        val extra = resultat.get("extra")
        assertEquals(3, extra.size(), "Extra skal inneholde resten")
    }

    fun sendMelding(melding: Map<String, Any>): JsonNode {
        val packet = JsonMessage.newMessage(melding)
        val results = objectMapper.createObjectNode()
        val løsning1 = objectMapper.createObjectNode()
        løsning1.put("Løsning1", "Verdi1")
        val løsning2 = objectMapper.createObjectNode()
        løsning2.put("Løsning2", "Verdi2")
        results.plusElement(løsning1)
        results.plusElement(løsning2)
        return mapExtraPacket(results, packet, objectMapper)
    }
}
