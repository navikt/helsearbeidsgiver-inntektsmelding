package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.json.readFail
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.db.INNTEKTSMELDING_DOKUMENT
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class LagreJournalpostIdLoeserTest {

    private val testRapid = TestRapid()
    private val inntektsmeldingRepo = mockk<InntektsmeldingRepository>()

    init {
        LagreJournalpostIdLoeser(testRapid, inntektsmeldingRepo)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        clearAllMocks()
    }

    @Test
    fun `skal lagre journalpostId i databasen`() {
        coEvery { inntektsmeldingRepo.oppdaterJournalpostId(any(), any()) } just Runs
        coEvery { inntektsmeldingRepo.hentNyeste(any()) } returns INNTEKTSMELDING_DOKUMENT

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(EventName.serializer()),
            Key.BEHOV to BehovType.LAGRE_JOURNALPOST_ID.toJson(BehovType.serializer()),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
            Key.JOURNALPOST_ID to "123".toJson()
        )

        val publisert = testRapid.firstMessage().toMap()

        Key.EVENT_NAME.lesOrNull(EventName.serializer(), publisert) shouldBe EventName.INNTEKTSMELDING_JOURNALFOERT
        Key.JOURNALPOST_ID.lesOrNull(String.serializer(), publisert).shouldNotBeNull()
    }

    @Test
    fun `skal håndtere at journalpostId er null eller blank`() {
        coEvery { inntektsmeldingRepo.oppdaterJournalpostId(any(), any()) } just Runs

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(EventName.serializer()),
            Key.BEHOV to BehovType.LAGRE_JOURNALPOST_ID.toJson(BehovType.serializer()),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
            Key.JOURNALPOST_ID to "".toJson()
        )

        val feilmelding = testRapid.firstMessage().readFail().feilmelding

        feilmelding.shouldNotBeNull()
    }

    @Test
    fun `skal håndtere feil ved lagring`() {
        coEvery {
            inntektsmeldingRepo.oppdaterJournalpostId(any(), any())
        } throws Exception()

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(EventName.serializer()),
            Key.BEHOV to BehovType.LAGRE_JOURNALPOST_ID.toJson(BehovType.serializer()),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
            Key.JOURNALPOST_ID to "123".toJson()
        )

        val feilmelding = testRapid.firstMessage().readFail().feilmelding

        feilmelding.shouldNotBeNull()
    }
}
