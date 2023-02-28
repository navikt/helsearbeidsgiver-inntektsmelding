@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import io.ktor.http.HttpStatusCode
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.NotisType
import no.nav.helsearbeidsgiver.felles.test.resource.readResource
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
            mapOf(
                Key.BEHOV.str to BehovType.NOTIFIKASJON_TRENGER_IM,
                Key.ID.str to UUID.randomUUID(),
                Key.UUID.str to "uuid",
                Key.IDENTITETSNUMMER.str to FNR,
                Key.ORGNRUNDERENHET.str to ORGNR
            ),
            "opprettNySak/gyldig.json".readResource(),
            HttpStatusCode.OK
        )
        assertEquals(NOTIFIKASJON_ID, løsning.value)
        assertNull(løsning.error)
    }

    @Test
    fun `skal sende kvittering`() {
        val løsning = sendMessage(
            mapOf(
                Key.BEHOV.str to BehovType.NOTIFIKASJON_IM_MOTTATT,
                Key.ID.str to UUID.randomUUID(),
                Key.UUID.str to "uuid",
                Key.IDENTITETSNUMMER.str to FNR,
                Key.ORGNRUNDERENHET.str to ORGNR
            ),
            "opprettNySak/gyldig.json".readResource(),
            HttpStatusCode.OK
        )
        assertEquals(NOTIFIKASJON_ID, løsning.value)
        assertNull(løsning.error)
    }

    @Test
    fun `skal håndtere at klient feiler`() {
        val løsning = sendMessage(
            mapOf(
                Key.BEHOV.str to BehovType.NOTIFIKASJON_TRENGER_IM,
                Key.ID.str to UUID.randomUUID(),
                Key.UUID.str to "uuid",
                Key.IDENTITETSNUMMER.str to FNR,
                Key.ORGNRUNDERENHET.str to ORGNR
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
            mapOf(
                Key.BEHOV.str to BehovType.NOTIFIKASJON_TRENGER_IM,
                Key.ID.str to UUID.randomUUID(),
                Key.ORGNRUNDERENHET.str to ORGNR
            ),
            NOTIFIKASJON_ID,
            HttpStatusCode.Forbidden
        )
        assertNull(løsning.value)
        assertNotNull(løsning.error)
    }
}
