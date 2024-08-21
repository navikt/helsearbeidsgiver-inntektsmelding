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
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.august
import java.util.UUID

class LagreImRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockImRepo = mockk<InntektsmeldingRepository>()

        LagreImRiver(mockImRepo).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        context("inntektsmelding lagres") {
            withData(
                mapOf(
                    "hvis ingen andre inntektsmeldinger er mottatt" to
                        EksisterendeInnsendinger(
                            eksisterendeSkjema = null,
                            eksisterendeInntektsmelding = null,
                        ),
                    "hvis ikke duplikat av tidligere inntektsmeldinger" to
                        EksisterendeInnsendinger(
                            eksisterendeSkjema = null,
                            eksisterendeInntektsmelding =
                                mockInntektsmelding().copy(
                                    fraværsperioder = listOf(9.august til 29.august),
                                ),
                        ),
                ),
            ) { eksisterendeInnsendinger ->
                every { mockImRepo.hentNyesteInntektsmelding(any()) } returns eksisterendeInnsendinger.eksisterendeInntektsmelding
                every { mockImRepo.oppdaterInntektsmeldingMedDokument(any(), any(), any()) } just Runs

                val nyInntektsmelding = mockInntektsmelding()

                val innkommendeMelding = innkommendeMelding(nyInntektsmelding)

                testRapid.sendJson(innkommendeMelding.toMap())

                testRapid.inspektør.size shouldBeExactly 1

                testRapid.firstMessage().toMap() shouldContainExactly
                    mapOf(
                        Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                        Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                        Key.DATA to
                            mapOf(
                                Key.FORESPOERSEL_ID to innkommendeMelding.forespoerselId.toJson(),
                                Key.INNTEKTSMELDING to innkommendeMelding.inntektsmelding.toJson(Inntektsmelding.serializer()),
                                Key.ER_DUPLIKAT_IM to false.toJson(Boolean.serializer()),
                            ).toJson(),
                    )

                verifySequence {
                    mockImRepo.hentNyesteInntektsmelding(innkommendeMelding.forespoerselId)
                    mockImRepo.oppdaterInntektsmeldingMedDokument(innkommendeMelding.forespoerselId, 1L, nyInntektsmelding)
                }
            }
        }

        test("duplikat lagres ikke, men svarer OK") {
            val nyInntektsmelding = mockInntektsmelding()

            val duplikatIm =
                nyInntektsmelding.copy(
                    vedtaksperiodeId = UUID.randomUUID(),
                    innsenderNavn = "Krokete Krølltang",
                    årsakInnsending = AarsakInnsending.NY,
                    tidspunkt = nyInntektsmelding.tidspunkt.minusDays(14),
                )

            every { mockImRepo.hentNyesteInntektsmelding(any()) } returns duplikatIm
            every { mockImRepo.oppdaterInntektsmeldingMedDokument(any(), any(), any()) } just Runs

            val innkommendeMelding = innkommendeMelding(nyInntektsmelding)

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                    Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FORESPOERSEL_ID to innkommendeMelding.forespoerselId.toJson(),
                            Key.INNTEKTSMELDING to innkommendeMelding.inntektsmelding.toJson(Inntektsmelding.serializer()),
                            Key.ER_DUPLIKAT_IM to true.toJson(Boolean.serializer()),
                        ).toJson(),
                )

            verifySequence {
                mockImRepo.hentNyesteInntektsmelding(innkommendeMelding.forespoerselId)
            }
            verify(exactly = 0) {
                mockImRepo.oppdaterInntektsmeldingMedDokument(innkommendeMelding.forespoerselId, 1L, nyInntektsmelding)
            }
        }

        test("håndterer at repo feiler") {
            every {
                mockImRepo.hentNyesteInntektsmelding(any())
            } throws RuntimeException("thank you, next")

            val innkommendeMelding = innkommendeMelding()

            val forventetFail =
                Fail(
                    feilmelding = "Klarte ikke lagre inntektsmelding i database.",
                    event = innkommendeMelding.eventName,
                    transaksjonId = innkommendeMelding.transaksjonId,
                    forespoerselId = innkommendeMelding.forespoerselId,
                    utloesendeMelding = innkommendeMelding.toMap().toJson(),
                )

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()

            verifySequence {
                mockImRepo.hentNyesteInntektsmelding(any())
            }
            verify(exactly = 0) {
                mockImRepo.lagreInntektsmelding(any(), any())
            }
        }

        context("ignorerer melding") {
            withData(
                mapOf(
                    "melding med uønsket behov" to Pair(Key.BEHOV, BehovType.HENT_VIRKSOMHET_NAVN.toJson()),
                    "melding med data som flagg" to Pair(Key.DATA, "".toJson()),
                    "melding med fail" to Pair(Key.FAIL, mockFail.toJson(Fail.serializer())),
                ),
            ) { uoensketKeyMedVerdi ->
                testRapid.sendJson(
                    innkommendeMelding()
                        .toMap()
                        .plus(uoensketKeyMedVerdi),
                )

                testRapid.inspektør.size shouldBeExactly 0

                verify(exactly = 0) {
                    mockImRepo.hentNyesteInntektsmelding(any())
                    mockImRepo.lagreInntektsmelding(any(), any())
                }
            }
        }
    })

private fun innkommendeMelding(inntektsmelding: Inntektsmelding = mockInntektsmelding()): LagreImMelding {
    val forespoerselId = UUID.randomUUID()

    return LagreImMelding(
        eventName = EventName.INNTEKTSMELDING_SKJEMA_LAGRET,
        behovType = BehovType.LAGRE_IM,
        transaksjonId = UUID.randomUUID(),
        data =
            mapOf(
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
            ),
        forespoerselId = forespoerselId,
        inntektsmelding = inntektsmelding,
        innsendingId = 1L,
    )
}

private fun LagreImMelding.toMap(): Map<Key, JsonElement> =
    mapOf(
        Key.EVENT_NAME to eventName.toJson(),
        Key.BEHOV to behovType.toJson(),
        Key.UUID to transaksjonId.toJson(),
        Key.DATA to data.toJson(),
    )

private val mockFail =
    Fail(
        feilmelding = "My name is Inigo Montoya...",
        event = EventName.INSENDING_STARTED,
        transaksjonId = UUID.randomUUID(),
        forespoerselId = UUID.randomUUID(),
        utloesendeMelding = JsonNull,
    )
