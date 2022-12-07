@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import io.ktor.http.HttpStatusCode
import no.nav.helsearbeidsgiver.felles.BehovType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

internal class NotifikasjonLøserTest : RapidMock() {

    private val FNR = "fnr-1"
    private val ORGNR = "orgnr-1"
    private val NOTIFIKASJON_ID = "355"

    @Test
    fun `skal sende trenger inntektsmelding`() {
        val løsning = sendMessage(
            BehovType.NOTIFIKASJON_TRENGER_IM,
            mapOf(
                "@behov" to listOf(BehovType.NOTIFIKASJON_TRENGER_IM.name),
                "@id" to UUID.randomUUID(),
                "uuid" to "uuid",
                "identitetsnummer" to FNR,
                "orgnrUnderenhet" to ORGNR
            ),
            readResource("opprettNySak/gyldig.json"),
            HttpStatusCode.OK
        )
        assertEquals(NOTIFIKASJON_ID, løsning.value)
        assertNull(løsning.error)
    }

    @Test
    fun `skal sende kvittering`() {
        val løsning = sendMessage(
            BehovType.NOTIFIKASJON,
            mapOf(
                "@behov" to listOf(BehovType.NOTIFIKASJON),
                "@id" to UUID.randomUUID(),
                "uuid" to "uuid",
                "identitetsnummer" to FNR,
                "orgnrUnderenhet" to ORGNR
            ),
            readResource("opprettNySak/gyldig.json"),
            HttpStatusCode.OK
        )
        assertEquals(NOTIFIKASJON_ID, løsning.value)
        assertNull(løsning.error)
    }

    @Test
    fun `skal håndtere at klient feiler`() {
        val løsning = sendMessage(
            BehovType.NOTIFIKASJON,
            mapOf(
                "@behov" to listOf(BehovType.NOTIFIKASJON),
                "@id" to UUID.randomUUID(),
                "uuid" to "uuid",
                "identitetsnummer" to FNR,
                "orgnrUnderenhet" to ORGNR
            ),
            NOTIFIKASJON_ID,
            HttpStatusCode.InternalServerError
        )
        assertNull(løsning.value)
        assertNotNull(løsning.error)
    }

    @Test
    fun `skal håndtere ukjente feil`() {
        val løsning = sendMessage(
            BehovType.NOTIFIKASJON,
            mapOf(
                "@behov" to listOf(BehovType.NOTIFIKASJON),
                "@id" to UUID.randomUUID(),
                "orgnrUnderenhet" to ORGNR
            ),
            NOTIFIKASJON_ID,
            HttpStatusCode.Forbidden
        )
        assertNull(løsning.value)
        assertNotNull(løsning.error)
    }
}
