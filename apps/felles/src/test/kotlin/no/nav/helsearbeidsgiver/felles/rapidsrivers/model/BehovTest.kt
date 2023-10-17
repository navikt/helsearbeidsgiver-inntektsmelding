package no.nav.helsearbeidsgiver.felles.rapidsrivers.model

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.util.UUID

class BehovTest {

    val behov = BehovType.FULLT_NAVN
    val event = EventName.FORESPØRSEL_LAGRET

    @Test
    fun createFail() {
        val behov = Behov(
            event,
            behov,
            UUID.randomUUID().toString(),
            JsonMessage.newMessage(
                mapOf(
                    Key.BEHOV.str to behov,
                    Key.EVENT_NAME.str to event,
                    "hepp" to "hei"
                )
            )
        )
        val feilmelding = "feilmelding"
        val fail = behov.createFail(feilmelding)
        val message = fail.toJsonMessage()
        println(message.toJson())
        assertFalse(message[Key.FAIL.str].isNull)
        assertEquals(feilmelding, message[Key.FAIL.str].asText())
        assertFalse(message[Key.FAILED_BEHOV.str].isNull)
    }
}
