package no.nav.helsearbeidsgiver.inntektsmelding.db.river

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
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.randomDigitString
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.db.INNTEKTSMELDING_DOKUMENT
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.SelvbestemtImRepo
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.Mock.INNSENDING_ID
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.Mock.toMap
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding as InntektsmeldingV1

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
                every { mockImRepo.oppdaterJournalpostId(any(), any()) } just Runs
                every { mockImRepo.hentNyesteBerikedeInnsendingId(any()) } returns INNSENDING_ID

                val innkommendeMelding =
                    Mock.innkommendeMelding(
                        InntektsmeldingV1.Type.Forespurt(
                            id = UUID.randomUUID(),
                            vedtaksperiodeId = UUID.randomUUID(),
                        ),
                    )

                testRapid.sendJson(innkommendeMelding.toMap())

                testRapid.inspektør.size shouldBeExactly 1

                testRapid.firstMessage().toMap() shouldContainExactly
                    mapOf(
                        Key.EVENT_NAME to EventName.INNTEKTSMELDING_JOURNALFOERT.toJson(),
                        Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                        Key.JOURNALPOST_ID to innkommendeMelding.journalpostId.toJson(),
                        Key.INNTEKTSMELDING_DOKUMENT to INNTEKTSMELDING_DOKUMENT.toJson(Inntektsmelding.serializer()),
                        Key.FORESPOERSEL_ID to innkommendeMelding.inntektsmeldingType.id.toJson(),
                    )

                verifySequence {
                    mockImRepo.oppdaterJournalpostId(INNSENDING_ID, innkommendeMelding.journalpostId)
                    mockImRepo.hentNyesteBerikedeInnsendingId(innkommendeMelding.inntektsmeldingType.id)
                }
                verify(exactly = 0) {
                    mockSelvbestemtImRepo.oppdaterJournalpostId(any(), any())
                }
            }

            test("selvbestemt IM") {
                every { mockSelvbestemtImRepo.oppdaterJournalpostId(any(), any()) } just Runs

                val innkommendeMelding =
                    Mock.innkommendeMelding(
                        InntektsmeldingV1.Type.Selvbestemt(
                            id = UUID.randomUUID(),
                            vedtaksperiodeId = UUID.randomUUID(),
                        ),
                    )

                testRapid.sendJson(innkommendeMelding.toMap())

                testRapid.inspektør.size shouldBeExactly 1

                testRapid.firstMessage().toMap() shouldContainExactly
                    mapOf(
                        Key.EVENT_NAME to EventName.INNTEKTSMELDING_JOURNALFOERT.toJson(),
                        Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                        Key.JOURNALPOST_ID to innkommendeMelding.journalpostId.toJson(),
                        Key.INNTEKTSMELDING_DOKUMENT to INNTEKTSMELDING_DOKUMENT.toJson(Inntektsmelding.serializer()),
                        Key.SELVBESTEMT_ID to innkommendeMelding.inntektsmeldingType.id.toJson(),
                    )

                verifySequence {
                    mockSelvbestemtImRepo.oppdaterJournalpostId(innkommendeMelding.inntektsmeldingType.id, innkommendeMelding.journalpostId)
                }
                verify(exactly = 0) {
                    mockImRepo.oppdaterJournalpostId(any(), any())
                }
            }
        }

        test("journalpost-ID lagres i databasen, men blir ikke sendt videre fordi IM ikke er nyeste innsending") {
            every { mockImRepo.oppdaterJournalpostId(any(), any()) } just Runs
            every { mockImRepo.hentNyesteBerikedeInnsendingId(any()) } returns INNSENDING_ID + 1L

            val innkommendeMelding =
                Mock.innkommendeMelding(
                    InntektsmeldingV1.Type.Forespurt(
                        id = UUID.randomUUID(),
                        vedtaksperiodeId = UUID.randomUUID(),
                    ),
                )

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 0

            verifySequence {
                mockImRepo.oppdaterJournalpostId(INNSENDING_ID, innkommendeMelding.journalpostId)
                mockImRepo.hentNyesteBerikedeInnsendingId(innkommendeMelding.inntektsmeldingType.id)
            }
            verify(exactly = 0) {
                mockSelvbestemtImRepo.oppdaterJournalpostId(any(), any())
            }
        }

        context("håndterer feil under lagring") {
            test("forespurt IM") {
                every { mockImRepo.oppdaterJournalpostId(any(), any()) } throws Exception()

                val innkommendeMelding =
                    Mock.innkommendeMelding(
                        InntektsmeldingV1.Type.Forespurt(
                            id = UUID.randomUUID(),
                            vedtaksperiodeId = UUID.randomUUID(),
                        ),
                    )

                val forventetFail =
                    Fail(
                        feilmelding = "Klarte ikke lagre journalpost-ID '${innkommendeMelding.journalpostId}'.",
                        event = innkommendeMelding.eventName,
                        transaksjonId = innkommendeMelding.transaksjonId,
                        forespoerselId = innkommendeMelding.inntektsmeldingType.id,
                        utloesendeMelding = innkommendeMelding.toMap().toJson(),
                    )

                testRapid.sendJson(innkommendeMelding.toMap())

                testRapid.inspektør.size shouldBeExactly 1

                testRapid.firstMessage().toMap() shouldContainExactly
                    forventetFail
                        .tilMelding()
                        .plus(Key.FORESPOERSEL_ID to innkommendeMelding.inntektsmeldingType.id.toJson())

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
                        InntektsmeldingV1.Type.Selvbestemt(
                            id = UUID.randomUUID(),
                            vedtaksperiodeId = UUID.randomUUID(),
                        ),
                    )

                val forventetFail =
                    Fail(
                        feilmelding = "Klarte ikke lagre journalpost-ID '${innkommendeMelding.journalpostId}'.",
                        event = innkommendeMelding.eventName,
                        transaksjonId = innkommendeMelding.transaksjonId,
                        forespoerselId = null,
                        utloesendeMelding = innkommendeMelding.toMap().toJson(),
                    )

                testRapid.sendJson(innkommendeMelding.toMap())

                testRapid.inspektør.size shouldBeExactly 1

                testRapid.firstMessage().toMap() shouldContainExactly
                    forventetFail
                        .tilMelding()
                        .minus(Key.FORESPOERSEL_ID)
                        .plus(Key.SELVBESTEMT_ID to innkommendeMelding.inntektsmeldingType.id.toJson())

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
                    "melding med ukjent behov" to Pair(Key.BEHOV, BehovType.LAGRE_EKSTERN_INNTEKTSMELDING.toJson()),
                    "melding med data" to Pair(Key.DATA, "".toJson()),
                    "melding med fail" to Pair(Key.FAIL, Mock.fail.toJson(Fail.serializer())),
                ),
            ) { uoensketKeyMedVerdi ->
                testRapid.sendJson(
                    Mock
                        .innkommendeMelding(
                            InntektsmeldingV1.Type.Forespurt(
                                id = UUID.randomUUID(),
                                vedtaksperiodeId = UUID.randomUUID(),
                            ),
                        ).toMap()
                        .plus(uoensketKeyMedVerdi),
                )

                testRapid.inspektør.size shouldBeExactly 0

                verify(exactly = 0) {
                    mockImRepo.oppdaterJournalpostId(any(), any())
                    mockSelvbestemtImRepo.oppdaterJournalpostId(any(), any())
                }
            }

            test("melding mangler både forespoerselId og selvbestemtId") {
                testRapid.sendJson(
                    Mock
                        .innkommendeMelding(
                            InntektsmeldingV1.Type.Selvbestemt(
                                id = UUID.randomUUID(),
                                vedtaksperiodeId = UUID.randomUUID(),
                            ),
                        ).toMap()
                        .minus(Key.SELVBESTEMT_ID),
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
    const val INNSENDING_ID = 1L

    fun innkommendeMelding(inntektsmeldingType: InntektsmeldingV1.Type): LagreJournalpostIdMelding =
        LagreJournalpostIdMelding(
            eventName = EventName.INNTEKTSMELDING_MOTTATT,
            behovType = BehovType.LAGRE_JOURNALPOST_ID,
            transaksjonId = UUID.randomUUID(),
            inntektsmeldingType = inntektsmeldingType,
            journalpostId = randomDigitString(10),
        )

    fun LagreJournalpostIdMelding.toMap(): Map<Key, JsonElement> =
        when (inntektsmeldingType) {
            is InntektsmeldingV1.Type.Forespurt -> {
                mapOf(
                    Key.EVENT_NAME to eventName.toJson(),
                    Key.BEHOV to behovType.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.INNTEKTSMELDING_DOKUMENT to INNTEKTSMELDING_DOKUMENT.toJson(Inntektsmelding.serializer()),
                    Key.JOURNALPOST_ID to journalpostId.toJson(),
                    Key.FORESPOERSEL_ID to inntektsmeldingType.id.toJson(),
                    Key.INNSENDING_ID to INNSENDING_ID.toJson(Long.serializer()),
                )
            }

            is InntektsmeldingV1.Type.Selvbestemt -> {
                mapOf(
                    Key.EVENT_NAME to eventName.toJson(),
                    Key.BEHOV to behovType.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.INNTEKTSMELDING_DOKUMENT to INNTEKTSMELDING_DOKUMENT.toJson(Inntektsmelding.serializer()),
                    Key.JOURNALPOST_ID to journalpostId.toJson(),
                    Key.SELVBESTEMT_ID to inntektsmeldingType.id.toJson(),
                )
            }
        }

    val fail =
        Fail(
            feilmelding = "I er et steinras og du skal falla med meg.",
            event = EventName.INNTEKTSMELDING_MOTTATT,
            transaksjonId = UUID.randomUUID(),
            forespoerselId = UUID.randomUUID(),
            utloesendeMelding = JsonNull,
        )
}
