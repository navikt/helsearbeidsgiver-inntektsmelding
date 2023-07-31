@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.brreg

import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.json.fromJsonMapOnlyKeys
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class VirksomhetLøserTest {

    private val testRapid = TestRapid()
    private val mockBrregClient = mockk<BrregClient>()

    private val ORGNR = "orgnr-1"
    private val VIRKSOMHET_NAVN = "Norge AS"

    init {
        VirksomhetLøser(testRapid, mockBrregClient, false)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `skal håndtere at klient feiler`() {
        coEvery { mockBrregClient.hentVirksomhetNavn(any()) } returns null

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
            Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
            DataFelt.ORGNRUNDERENHET to ORGNR.toJson(),
            Key.UUID to UUID.randomUUID().toJson()
        )

        val loesning = testRapid.firstMessage().lesLoesning()

        assertEquals("Ugyldig virksomhet $ORGNR", loesning.error?.melding)
    }

    @Test
    fun `skal returnere løsning når gyldige data`() {
        coEvery { mockBrregClient.hentVirksomhetNavn(any()) } returns VIRKSOMHET_NAVN

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
            Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
            DataFelt.ORGNRUNDERENHET to ORGNR.toJson(),
            Key.UUID to UUID.randomUUID().toJson()
        )

        val loesning = testRapid.firstMessage().lesLoesning()

        assertEquals(VIRKSOMHET_NAVN, loesning.value)
    }

    @Test
    fun `skal håndtere ukjente feil`() {
        testRapid.sendJson(
            Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
            Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
            DataFelt.ORGNRUNDERENHET to ORGNR.toJson(),
            Key.UUID to UUID.randomUUID().toJson()
        )

        val loesning = testRapid.firstMessage().lesLoesning()

        assertNotNull(loesning.error)
    }
}

private fun JsonElement.lesLoesning(): VirksomhetLøsning =
    fromJsonMapOnlyKeys()
        .get(Key.LØSNING)
        .shouldNotBeNull()
        .fromJsonMapFiltered(BehovType.serializer())
        .get(BehovType.VIRKSOMHET)
        .shouldNotBeNull()
        .fromJson(VirksomhetLøsning.serializer())
