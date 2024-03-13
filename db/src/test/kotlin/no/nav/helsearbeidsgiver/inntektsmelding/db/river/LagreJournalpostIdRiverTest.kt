package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.db.AapenImRepo
import no.nav.helsearbeidsgiver.inntektsmelding.db.INNTEKTSMELDING_DOKUMENT
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class LagreJournalpostIdRiverTest : FunSpec({

    val testRapid = TestRapid()
    val mockImRepo = mockk<InntektsmeldingRepository>()
    val mockAapenImRepo = mockk<AapenImRepo>()

    LagreJournalpostIdRiver(mockImRepo, mockAapenImRepo).connect(testRapid)

    beforeTest {
        testRapid.reset()
        clearAllMocks()
    }

    context("journalpost-ID lagres i databasen") {
        test("forespurt IM") {
            every { mockImRepo.oppdaterJournalpostId(any(), any()) } just Runs

            val transaksjonId = UUID.randomUUID()
            val journalpostId = "123345343"
            val forespoerselId = UUID.randomUUID()

            testRapid.sendJson(
                Mock.innkommendeMeldingUtenImId(transaksjonId, journalpostId)
                    .plus(Key.FORESPOERSEL_ID to forespoerselId.toJson())
            )

            testRapid.inspektør.size shouldBeExactly 1

            val publisert = testRapid.firstMessage().toMap()

            Key.EVENT_NAME.lesOrNull(EventName.serializer(), publisert) shouldBe EventName.INNTEKTSMELDING_JOURNALFOERT
            Key.UUID.lesOrNull(UuidSerializer, publisert) shouldBe transaksjonId
            Key.JOURNALPOST_ID.lesOrNull(String.serializer(), publisert) shouldBe journalpostId
            Key.INNTEKTSMELDING_DOKUMENT.lesOrNull(Inntektsmelding.serializer(), publisert) shouldBe INNTEKTSMELDING_DOKUMENT
            Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, publisert) shouldBe forespoerselId
            Key.AAPEN_ID.lesOrNull(UuidSerializer, publisert).shouldBeNull()

            verifySequence {
                mockImRepo.oppdaterJournalpostId(forespoerselId, journalpostId)
            }
            verify(exactly = 0) {
                mockAapenImRepo.oppdaterJournalpostId(any(), any())
            }
        }

        test("aapen IM") {
            every { mockAapenImRepo.oppdaterJournalpostId(any(), any()) } just Runs

            val transaksjonId = UUID.randomUUID()
            val journalpostId = "84294234"
            val aapenId = UUID.randomUUID()

            testRapid.sendJson(
                Mock.innkommendeMeldingUtenImId(transaksjonId, journalpostId)
                    .plus(Key.AAPEN_ID to aapenId.toJson())
            )

            testRapid.inspektør.size shouldBeExactly 1

            val publisert = testRapid.firstMessage().toMap()

            Key.EVENT_NAME.lesOrNull(EventName.serializer(), publisert) shouldBe EventName.INNTEKTSMELDING_JOURNALFOERT
            Key.UUID.lesOrNull(UuidSerializer, publisert) shouldBe transaksjonId
            Key.JOURNALPOST_ID.lesOrNull(String.serializer(), publisert) shouldBe journalpostId
            Key.INNTEKTSMELDING_DOKUMENT.lesOrNull(Inntektsmelding.serializer(), publisert) shouldBe INNTEKTSMELDING_DOKUMENT
            Key.AAPEN_ID.lesOrNull(UuidSerializer, publisert) shouldBe aapenId
            Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, publisert).shouldBeNull()

            verifySequence {
                mockAapenImRepo.oppdaterJournalpostId(aapenId, journalpostId)
            }
            verify(exactly = 0) {
                mockImRepo.oppdaterJournalpostId(any(), any())
            }
        }
    }

    context("håndterer feil under lagring") {
        test("forespurt IM") {
            every { mockImRepo.oppdaterJournalpostId(any(), any()) } throws Exception()

            val eventName = EventName.INNTEKTSMELDING_MOTTATT
            val transaksjonId = UUID.randomUUID()
            val journalpostId = "1134250053"
            val forespoerselId = UUID.randomUUID()

            val innkommendeMelding = Mock.innkommendeMeldingUtenImId(transaksjonId, journalpostId)
                .plus(Key.FORESPOERSEL_ID to forespoerselId.toJson())

            val forventetFail = Fail(
                feilmelding = "Klarte ikke lagre journalpost-ID '$journalpostId'.",
                event = eventName,
                transaksjonId = transaksjonId,
                forespoerselId = forespoerselId,
                utloesendeMelding = innkommendeMelding.toJson()
            )

            testRapid.sendJson(innkommendeMelding)

            testRapid.inspektør.size shouldBeExactly 1

            val publisert = testRapid.firstMessage().toMap()

            Key.FAIL.lesOrNull(Fail.serializer(), publisert) shouldBe forventetFail
            Key.EVENT_NAME.lesOrNull(EventName.serializer(), publisert) shouldBe eventName
            Key.UUID.lesOrNull(UuidSerializer, publisert) shouldBe transaksjonId
            Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, publisert) shouldBe forespoerselId
            Key.AAPEN_ID.lesOrNull(UuidSerializer, publisert).shouldBeNull()

            verifySequence {
                mockImRepo.oppdaterJournalpostId(any(), any())
            }
            verify(exactly = 0) {
                mockAapenImRepo.oppdaterJournalpostId(any(), any())
            }
        }

        test("aapen IM") {
            every { mockAapenImRepo.oppdaterJournalpostId(any(), any()) } throws Exception()

            val eventName = EventName.INNTEKTSMELDING_MOTTATT
            val transaksjonId = UUID.randomUUID()
            val journalpostId = "1134250053"
            val aapenId = UUID.randomUUID()

            val innkommendeMelding = Mock.innkommendeMeldingUtenImId(transaksjonId, journalpostId)
                .plus(Key.AAPEN_ID to aapenId.toJson())

            val forventetFail = Fail(
                feilmelding = "Klarte ikke lagre journalpost-ID '$journalpostId'.",
                event = eventName,
                transaksjonId = transaksjonId,
                forespoerselId = null,
                utloesendeMelding = innkommendeMelding.toJson()
            )

            testRapid.sendJson(innkommendeMelding)

            testRapid.inspektør.size shouldBeExactly 1

            val publisert = testRapid.firstMessage().toMap()

            Key.FAIL.lesOrNull(Fail.serializer(), publisert) shouldBe forventetFail
            Key.EVENT_NAME.lesOrNull(EventName.serializer(), publisert) shouldBe eventName
            Key.UUID.lesOrNull(UuidSerializer, publisert) shouldBe transaksjonId
            Key.AAPEN_ID.lesOrNull(UuidSerializer, publisert) shouldBe aapenId
            Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, publisert).shouldBeNull()

            verifySequence {
                mockAapenImRepo.oppdaterJournalpostId(any(), any())
            }
            verify(exactly = 0) {
                mockImRepo.oppdaterJournalpostId(any(), any())
            }
        }
    }

    context("ignorerer melding") {
        withData(
            mapOf(
                "melding med data" to Pair(Key.DATA, "".toJson()),
                "melding med fail" to Pair(Key.FAIL, Mock.fail.toJson(Fail.serializer()))
            )
        ) { uoensketKeyMedVerdi ->
            testRapid.sendJson(
                Mock.innkommendeMeldingUtenImId(UUID.randomUUID(), "4283487389")
                    .plus(Key.FORESPOERSEL_ID to UUID.randomUUID().toJson())
                    .plus(uoensketKeyMedVerdi)
            )

            testRapid.inspektør.size shouldBeExactly 0

            verify(exactly = 0) {
                mockImRepo.oppdaterJournalpostId(any(), any())
                mockAapenImRepo.oppdaterJournalpostId(any(), any())
            }
        }

        test("melding mangler både forespoerselId og aapenId") {
            testRapid.sendJson(
                Mock.innkommendeMeldingUtenImId(UUID.randomUUID(), "6837506")
            )

            testRapid.inspektør.size shouldBeExactly 0

            verify(exactly = 0) {
                mockImRepo.oppdaterJournalpostId(any(), any())
                mockAapenImRepo.oppdaterJournalpostId(any(), any())
            }
        }

        test("melding med ukjent behov") {
            testRapid.sendJson(
                Mock.innkommendeMeldingUtenImId(UUID.randomUUID(), "2490583")
                    .plus(Key.FORESPOERSEL_ID to UUID.randomUUID().toJson())
                    .plus(Key.BEHOV to BehovType.LAGRE_EKSTERN_INNTEKTSMELDING.toJson())
            )

            testRapid.inspektør.size shouldBeExactly 0

            verify(exactly = 0) {
                mockImRepo.oppdaterJournalpostId(any(), any())
                mockAapenImRepo.oppdaterJournalpostId(any(), any())
            }
        }
    }
})

private object Mock {
    val fail = Fail(
        feilmelding = "I er et steinras og du skal falla med meg.",
        event = EventName.INNTEKTSMELDING_MOTTATT,
        transaksjonId = UUID.randomUUID(),
        forespoerselId = UUID.randomUUID(),
        utloesendeMelding = JsonNull
    )

    fun innkommendeMeldingUtenImId(transaksjonId: UUID, journalpostId: String): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
            Key.BEHOV to BehovType.LAGRE_JOURNALPOST_ID.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.INNTEKTSMELDING_DOKUMENT to INNTEKTSMELDING_DOKUMENT.toJson(Inntektsmelding.serializer()),
            Key.JOURNALPOST_ID to journalpostId.toJson()
        )
}
