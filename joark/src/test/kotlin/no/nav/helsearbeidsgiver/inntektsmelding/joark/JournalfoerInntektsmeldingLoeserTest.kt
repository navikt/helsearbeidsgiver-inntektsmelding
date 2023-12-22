package no.nav.helsearbeidsgiver.inntektsmelding.joark

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.prometheus.client.CollectorRegistry
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.dokarkiv.domene.OpprettOgFerdigstillResponse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.json.readFail
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

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
            Key.INNTEKTSMELDING_DOKUMENT to mockInntektsmelding().toJson(Inntektsmelding.serializer()),
            Key.UUID to UUID.randomUUID().toJson()
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

        val expectedUuid = UUID.randomUUID()

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
            Key.BEHOV to BehovType.JOURNALFOER.toJson(),
            Key.INNTEKTSMELDING_DOKUMENT to mockInntektsmelding().toJson(Inntektsmelding.serializer()),
            Key.UUID to expectedUuid.toJson()
        )

        val publisert = testRapid.firstMessage().toMap()

        publisert[Key.BEHOV]?.fromJson(BehovType.serializer()) shouldBe BehovType.LAGRE_JOURNALPOST_ID
        publisert[Key.JOURNALPOST_ID]?.fromJson(String.serializer()) shouldBe "jid-ulende-koala"
        publisert[Key.UUID]?.fromJson(UuidSerializer) shouldBe expectedUuid
    }

    @Test
    fun `skal håndtere ukjente feil`() {
        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
            Key.BEHOV to BehovType.JOURNALFOER.name.toJson(),
            Key.INNTEKTSMELDING_DOKUMENT to "xyz".toJson(),
            Key.UUID to UUID.randomUUID().toJson()
        )
        val fail = testRapid.firstMessage().readFail()
        assertTrue(fail.feilmelding.isNotEmpty())
    }
}
