package no.nav.helsearbeidsgiver.inntektsmelding.akkumulator

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

internal class MapExtraPacketKtTest {

    private val BEHOV_PDL = BehovType.FULLT_NAVN.toString()
    private val PDL_OK = NavnLøsning(value = "xyz")
    private val objectMapper = ObjectMapper()

    @Test
    fun skal_mappe_om_riktig() {
        val id = UUID.randomUUID()
        val melding = mapOf(
            "@id" to id,
            "uuid" to "uuid",
            "@behov" to listOf(BEHOV_PDL),
            "@extra" to BehovType.IM_VALIDERING.toString(),
            "@løsning" to mapOf(
                BEHOV_PDL to PDL_OK
            ),
            "inntektsmelding" to "placeholder"
        )
        val packet = JsonMessage.newMessage(melding)
        val nextPacket = mapExtraPacket(BehovType.IM_VALIDERING, packet, objectMapper)
        val behov = nextPacket["@behov"]
        assertNotEquals(id, nextPacket["@id"], "Skal få ny id")
        assertNull(nextPacket["@extra"], "Skal slette nye behov")
        assertNotNull(behov, "Skal finne behov")
        assertEquals("FULLT_NAVN", behov[0].asText(), "Skal beholde eksisterende behov")
        assertEquals("IM_VALIDERING", behov[1].asText(), "Skal legge til nytt behov")
        assertEquals("placeholder", nextPacket["inntektsmelding"].asText(), "Skal beholde eksisterende data")
    }
}
