@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.akkumulator

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.PersonDato
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class HentNesteBehovKtTest {

    private val FULLT_NAVN_OK = NavnLøsning(value = PersonDato("xyz", LocalDate.now()))
    private val objectMapper = ObjectMapper()

    @Test
    fun `skal håndtere at det ikke er neste behov`() {
        val resultat = sendMelding(
            mapOf(
                "uuid" to "uuid",
                "@behov" to listOf(BehovType.FULLT_NAVN, BehovType.INNTEKT),
                "@løsning" to mapOf(
                    BehovType.FULLT_NAVN.toString() to FULLT_NAVN_OK
                ),
                "neste_behov" to emptyList<String>()
            )
        )
        val behov = resultat[Key.BEHOV.str]
        assertEquals(2, behov.size(), "Skal beholde behov uforandret")
        assertEquals(BehovType.FULLT_NAVN.toString(), behov[0].asText())
        assertEquals(BehovType.INNTEKT.toString(), behov[1].asText())
        val neste_behov = resultat.get("neste_behov")
        assertEquals(0, neste_behov.size(), "neste_behov skal bli tom")
    }

    @Test
    fun `skal håndtere at det er et neste behov uten pause`() {
        val resultat = sendMelding(
            mapOf(
                "uuid" to "uuid",
                "@behov" to listOf(BehovType.FULLT_NAVN.toString(), BehovType.INNTEKT.toString()),
                "@løsning" to mapOf(
                    BehovType.FULLT_NAVN.toString() to FULLT_NAVN_OK
                ),
                "neste_behov" to listOf(BehovType.VIRKSOMHET)
            )
        )
        val behov = resultat[Key.BEHOV.str]
        assertEquals(3, behov.size(), "Skal beholde behov uforandret")
        assertEquals(BehovType.FULLT_NAVN.toString(), behov[0].asText())
        assertEquals(BehovType.INNTEKT.toString(), behov[1].asText())
        assertEquals(BehovType.VIRKSOMHET.toString(), behov[2].asText())
        val neste_behov = resultat.get("neste_behov")
        assertEquals(0, neste_behov.size(), "neste_behov skal bli tom")
    }

    @Test
    fun `skal håndtere at det er flere neste behov med pause`() {
        val resultat = sendMelding(
            mapOf(
                "@behov" to listOf(BehovType.FULLT_NAVN.toString(), BehovType.INNTEKT.toString()),
                "neste_behov" to listOf(BehovType.VIRKSOMHET, BehovType.PAUSE, BehovType.ARBEIDSGIVERE, BehovType.PAUSE, BehovType.JOURNALFOER)
            )
        )
        val behov = resultat[Key.BEHOV.str]
        assertEquals(3, behov.size(), "Skal beholde behov uforandret")
        assertEquals(BehovType.FULLT_NAVN.toString(), behov[0].asText())
        assertEquals(BehovType.INNTEKT.toString(), behov[1].asText())
        assertEquals(BehovType.VIRKSOMHET.toString(), behov[2].asText())
        val neste_behov = resultat.get("neste_behov")
        assertEquals(3, neste_behov.size(), "neste_behov skal inneholde resten")
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
        return hentNesteBehov(results, packet, objectMapper)
    }
}
