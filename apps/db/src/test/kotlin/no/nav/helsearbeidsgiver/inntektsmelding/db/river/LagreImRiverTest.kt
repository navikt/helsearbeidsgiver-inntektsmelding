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
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Utils.convert
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.mockFail
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class LagreImRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockImRepo = mockk<InntektsmeldingRepository>()

        val innsendingId = 1L

        LagreImRiver(mockImRepo).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        test("inntektsmelding lagres") {
            every { mockImRepo.oppdaterMedBeriketDokument(any(), any(), any()) } just Runs

            val nyInntektsmelding = mockInntektsmeldingV1()

            val innkommendeMelding = innkommendeMelding(innsendingId, nyInntektsmelding)

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                    Key.KONTEKST_ID to innkommendeMelding.kontekstId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.INNTEKTSMELDING to innkommendeMelding.inntektsmelding.toJson(Inntektsmelding.serializer()),
                            Key.ER_DUPLIKAT_IM to false.toJson(Boolean.serializer()),
                            Key.INNSENDING_ID to innsendingId.toJson(Long.serializer()),
                        ).toJson(),
                )

            verifySequence {
                mockImRepo.oppdaterMedBeriketDokument(innkommendeMelding.inntektsmelding.type.id, innsendingId, nyInntektsmelding.convert())
            }
        }

        test("håndterer at repo feiler") {
            every {
                mockImRepo.oppdaterMedBeriketDokument(any(), any(), any())
            } throws RuntimeException("thank you, next")

            val innkommendeMelding = innkommendeMelding(innsendingId)

            val forventetFail =
                Fail(
                    feilmelding = "Klarte ikke lagre inntektsmelding i database.",
                    kontekstId = innkommendeMelding.kontekstId,
                    utloesendeMelding = innkommendeMelding.toMap(),
                )

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()

            verifySequence {
                mockImRepo.oppdaterMedBeriketDokument(any(), any(), any())
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
                    innkommendeMelding(innsendingId)
                        .toMap()
                        .plus(uoensketKeyMedVerdi),
                )

                testRapid.inspektør.size shouldBeExactly 0

                verify(exactly = 0) {
                    mockImRepo.oppdaterMedBeriketDokument(any(), any(), any())
                }
            }
        }
    })

private fun innkommendeMelding(
    innsendingId: Long,
    inntektsmelding: Inntektsmelding = mockInntektsmeldingV1(),
): LagreImMelding =
    LagreImMelding(
        eventName = EventName.INNTEKTSMELDING_SKJEMA_LAGRET,
        behovType = BehovType.LAGRE_IM,
        kontekstId = UUID.randomUUID(),
        data =
            mapOf(
                Key.INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
                Key.INNSENDING_ID to innsendingId.toJson(Long.serializer()),
            ),
        inntektsmelding = inntektsmelding,
        innsendingId = innsendingId,
    )

private fun LagreImMelding.toMap(): Map<Key, JsonElement> =
    mapOf(
        Key.EVENT_NAME to eventName.toJson(),
        Key.BEHOV to behovType.toJson(),
        Key.KONTEKST_ID to kontekstId.toJson(),
        Key.DATA to data.toJson(),
    )

private val mockFail = mockFail("My name is Inigo Montoya...", EventName.INSENDING_STARTED)
