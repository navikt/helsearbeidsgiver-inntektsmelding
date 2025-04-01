package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.mockFail
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.felles.test.mock.randomDigitString
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.SelvbestemtImRepo
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.Mock.toMap
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class LagreJournalpostIdRiverTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockImRepo = mockk<InntektsmeldingRepository>()
        val mockSelvbestemtImRepo = mockk<SelvbestemtImRepo>()

        LagreJournalpostIdRiver(mockImRepo, mockSelvbestemtImRepo).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        context("journalpost-ID lagres i databasen") {
            test("forespurt IM") {
                val innkommendeMelding = Mock.innkommendeMelding()

                every { mockImRepo.oppdaterJournalpostId(any(), any()) } just Runs
                every { mockImRepo.hentNyesteBerikedeInntektsmeldingId(any()) } returns innkommendeMelding.inntektsmelding.id

                testRapid.sendJson(innkommendeMelding.toMap())

                testRapid.inspektør.size shouldBeExactly 1

                testRapid.firstMessage().toMap() shouldContainExactly
                    mapOf(
                        Key.EVENT_NAME to EventName.INNTEKTSMELDING_JOURNALPOST_ID_LAGRET.toJson(),
                        Key.KONTEKST_ID to innkommendeMelding.kontekstId.toJson(),
                        Key.INNTEKTSMELDING to innkommendeMelding.inntektsmelding.toJson(Inntektsmelding.serializer()),
                        Key.JOURNALPOST_ID to innkommendeMelding.journalpostId.toJson(),
                    )

                verifySequence {
                    mockImRepo.oppdaterJournalpostId(innkommendeMelding.inntektsmelding.id, innkommendeMelding.journalpostId)
                    mockImRepo.hentNyesteBerikedeInntektsmeldingId(innkommendeMelding.inntektsmelding.type.id)
                }
                verify(exactly = 0) {
                    mockSelvbestemtImRepo.oppdaterJournalpostId(any(), any())
                }
            }

            test("selvbestemt IM") {
                every { mockSelvbestemtImRepo.oppdaterJournalpostId(any(), any()) } just Runs

                val innkommendeMelding =
                    Mock.innkommendeMelding(
                        mockInntektsmeldingV1().copy(
                            type =
                                Inntektsmelding.Type.Selvbestemt(
                                    id = UUID.randomUUID(),
                                ),
                        ),
                    )

                testRapid.sendJson(innkommendeMelding.toMap())

                testRapid.inspektør.size shouldBeExactly 1

                testRapid.firstMessage().toMap() shouldContainExactly
                    mapOf(
                        Key.EVENT_NAME to EventName.INNTEKTSMELDING_JOURNALPOST_ID_LAGRET.toJson(),
                        Key.KONTEKST_ID to innkommendeMelding.kontekstId.toJson(),
                        Key.INNTEKTSMELDING to innkommendeMelding.inntektsmelding.toJson(Inntektsmelding.serializer()),
                        Key.JOURNALPOST_ID to innkommendeMelding.journalpostId.toJson(),
                    )

                verifySequence {
                    mockSelvbestemtImRepo.oppdaterJournalpostId(innkommendeMelding.inntektsmelding.id, innkommendeMelding.journalpostId)
                }
                verify(exactly = 0) {
                    mockImRepo.oppdaterJournalpostId(any(), any())
                }
            }
        }

        test("journalpost-ID lagres i databasen, men blir ikke sendt videre fordi IM ikke er nyeste innsending") {
            every { mockImRepo.oppdaterJournalpostId(any(), any()) } just Runs
            every { mockImRepo.hentNyesteBerikedeInntektsmeldingId(any()) } returns UUID.randomUUID()

            val innkommendeMelding = Mock.innkommendeMelding()

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 0

            verifySequence {
                mockImRepo.oppdaterJournalpostId(innkommendeMelding.inntektsmelding.id, innkommendeMelding.journalpostId)
                mockImRepo.hentNyesteBerikedeInntektsmeldingId(innkommendeMelding.inntektsmelding.type.id)
            }
            verify(exactly = 0) {
                mockSelvbestemtImRepo.oppdaterJournalpostId(any(), any())
            }
        }

        context("håndterer feil under lagring") {
            test("forespurt IM") {
                every { mockImRepo.oppdaterJournalpostId(any(), any()) } throws Exception()

                val innkommendeMelding = Mock.innkommendeMelding()

                val forventetFail =
                    Fail(
                        feilmelding = "Klarte ikke lagre journalpost-ID '${innkommendeMelding.journalpostId}'.",
                        kontekstId = innkommendeMelding.kontekstId,
                        utloesendeMelding = innkommendeMelding.toMap(),
                    )

                testRapid.sendJson(innkommendeMelding.toMap())

                testRapid.inspektør.size shouldBeExactly 1

                testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()

                verifySequence {
                    mockImRepo.oppdaterJournalpostId(any(), any())
                }
                verify(exactly = 0) {
                    mockSelvbestemtImRepo.oppdaterJournalpostId(any(), any())
                }
            }

            test("selvbestemt IM") {
                every { mockSelvbestemtImRepo.oppdaterJournalpostId(any(), any()) } throws Exception()

                val innkommendeMelding =
                    Mock.innkommendeMelding(
                        mockInntektsmeldingV1().copy(
                            type =
                                Inntektsmelding.Type.Selvbestemt(
                                    id = UUID.randomUUID(),
                                ),
                        ),
                    )

                val forventetFail =
                    Fail(
                        feilmelding = "Klarte ikke lagre journalpost-ID '${innkommendeMelding.journalpostId}'.",
                        kontekstId = innkommendeMelding.kontekstId,
                        utloesendeMelding = innkommendeMelding.toMap(),
                    )

                testRapid.sendJson(innkommendeMelding.toMap())

                testRapid.inspektør.size shouldBeExactly 1

                testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()

                verifySequence {
                    mockSelvbestemtImRepo.oppdaterJournalpostId(any(), any())
                }
                verify(exactly = 0) {
                    mockImRepo.oppdaterJournalpostId(any(), any())
                }
            }
        }

        context("ignorerer melding") {
            withData(
                mapOf(
                    "melding med ukjent behov" to Pair(Key.BEHOV, BehovType.LAGRE_IM_SKJEMA.toJson()),
                    "melding med data" to Pair(Key.DATA, "".toJson()),
                    "melding med fail" to Pair(Key.FAIL, Mock.fail.toJson(Fail.serializer())),
                ),
            ) { uoensketKeyMedVerdi ->
                testRapid.sendJson(
                    Mock
                        .innkommendeMelding()
                        .toMap()
                        .plus(uoensketKeyMedVerdi),
                )

                testRapid.inspektør.size shouldBeExactly 0

                verify(exactly = 0) {
                    mockImRepo.oppdaterJournalpostId(any(), any())
                    mockSelvbestemtImRepo.oppdaterJournalpostId(any(), any())
                }
            }
        }
    })

private object Mock {
    fun innkommendeMelding(inntektsmelding: Inntektsmelding = mockInntektsmeldingV1()): LagreJournalpostIdMelding =
        LagreJournalpostIdMelding(
            eventName = EventName.INNTEKTSMELDING_JOURNALFOERT,
            kontekstId = UUID.randomUUID(),
            inntektsmelding = inntektsmelding,
            journalpostId = randomDigitString(10),
        )

    fun LagreJournalpostIdMelding.toMap(): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.JOURNALPOST_ID to journalpostId.toJson(),
            Key.INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
        )

    val fail = mockFail("I er et steinras og du skal falla med meg.", EventName.INNTEKTSMELDING_MOTTATT)
}
