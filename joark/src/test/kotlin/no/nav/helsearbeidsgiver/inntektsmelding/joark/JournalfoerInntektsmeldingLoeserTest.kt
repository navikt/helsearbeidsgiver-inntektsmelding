package no.nav.helsearbeidsgiver.inntektsmelding.joark

import io.mockk.coEvery
import io.mockk.mockk
import io.prometheus.client.CollectorRegistry
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.dokarkiv.domene.OpprettOgFerdigstillResponse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.json.fromJsonMapOnlyKeys
import no.nav.helsearbeidsgiver.felles.test.json.readFail
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JournalfoerInntektsmeldingLoeserTest {

    private val testRapid = TestRapid()
    private val mockDokArkivClient = mockk<DokArkivClient>()

    init {
        JournalfoerInntektsmeldingLoeser(testRapid, mockDokArkivClient)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        CollectorRegistry.defaultRegistry.clear()
    }

    @Test
    fun `skal håndtere at dokarkiv feiler`() {
        val forventetFeilmelding = "Klarte ikke journalføre"

        coEvery {
            mockDokArkivClient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
        } throws RuntimeException(forventetFeilmelding)

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
            Key.BEHOV to BehovType.JOURNALFOER.toJson(),
            DataFelt.INNTEKTSMELDING_DOKUMENT to mockInntektsmelding().toJson(Inntektsmelding.serializer()),
            Key.UUID to "uuid-557".toJson()
        )

        val fail = testRapid.firstMessage().readFail()

        assertEquals(forventetFeilmelding, fail.feilmelding)
    }

    @Test
    fun `skal journalføre når gyldige data`() {
        coEvery {
            mockDokArkivClient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
        } returns OpprettOgFerdigstillResponse(
            journalpostId = "jid-ulende-koala",
            journalpostFerdigstilt = true,
            melding = "Ha en fin dag!",
            dokumenter = emptyList()
        )

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
            Key.BEHOV to BehovType.JOURNALFOER.toJson(),
            DataFelt.INNTEKTSMELDING_DOKUMENT to mockInntektsmelding().toJson(Inntektsmelding.serializer()),
            Key.UUID to "uuid-979".toJson()
        )

        val publisert = testRapid.firstMessage()
            .fromJsonMapOnlyKeys()
            .mapValues { (_, value) -> value.fromJson(String.serializer()) }

        assertEquals(BehovType.LAGRE_JOURNALPOST_ID.name, publisert[Key.BEHOV])
        assertEquals("jid-ulende-koala", publisert[Key.JOURNALPOST_ID])
        assertEquals("uuid-979", publisert[Key.UUID])
    }

    @Test
    fun `skal håndtere ukjente feil`() {
        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
            Key.BEHOV to BehovType.JOURNALFOER.name.toJson(),
            DataFelt.INNTEKTSMELDING_DOKUMENT to "xyz".toJson(),
            Key.UUID to "uuid-549".toJson()
        )
        val fail = testRapid.firstMessage().readFail()
        assertTrue(fail.feilmelding.isNotEmpty())
    }
}
