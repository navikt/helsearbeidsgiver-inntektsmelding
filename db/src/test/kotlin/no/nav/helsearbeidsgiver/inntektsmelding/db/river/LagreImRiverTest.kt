package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
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
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Utils.convert
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.felles.test.shouldContainAllExcludingTempKey
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.oktober
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

        context("inntektsmelding lagres") {
            withData(
                mapOf(
                    "hvis ingen andre inntektsmeldinger er mottatt" to null,
                    "hvis ikke duplikat av tidligere inntektsmeldinger" to
                        mockInntektsmeldingV1().copy(
                            sykmeldingsperioder = listOf(9.august til 29.august),
                        ),
                ),
            ) { eksisterendeInntektsmelding ->
                every { mockImRepo.hentNyesteInntektsmelding(any()) } returns eksisterendeInntektsmelding?.convert()
                every { mockImRepo.oppdaterMedBeriketDokument(any(), any(), any()) } just Runs

                val nyInntektsmelding = mockInntektsmeldingV1()

                val innkommendeMelding = innkommendeMelding(innsendingId, nyInntektsmelding)

                testRapid.sendJson(innkommendeMelding.toMap())

                testRapid.inspektør.size shouldBeExactly 1

                testRapid.firstMessage().toMap() shouldContainAllExcludingTempKey
                    mapOf(
                        Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                        Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                        Key.DATA to
                            mapOf(
                                Key.INNTEKTSMELDING to innkommendeMelding.inntektsmelding.toJson(Inntektsmelding.serializer()),
                                Key.BESTEMMENDE_FRAVAERSDAG to bestemmendeFravaersdag.toJson(),
                                Key.ER_DUPLIKAT_IM to false.toJson(Boolean.serializer()),
                                Key.INNSENDING_ID to innsendingId.toJson(Long.serializer()),
                            ).toJson(),
                    )

                verifySequence {
                    mockImRepo.hentNyesteInntektsmelding(innkommendeMelding.inntektsmelding.type.id)
                    mockImRepo.oppdaterMedBeriketDokument(innkommendeMelding.inntektsmelding.type.id, innsendingId, nyInntektsmelding.convert())
                }
            }
        }

        test("duplikat lagres ikke, men svarer OK") {
            val nyInntektsmelding = mockInntektsmeldingV1()

            val duplikatIm =
                nyInntektsmelding.let {
                    it.copy(
                        vedtaksperiodeId = UUID.randomUUID(),
                        avsender =
                            it.avsender.copy(
                                navn = "Krokete Krølltang",
                            ),
                        aarsakInnsending = AarsakInnsending.Ny,
                        mottatt = nyInntektsmelding.mottatt.minusDays(14),
                    )
                }

            every { mockImRepo.hentNyesteInntektsmelding(any()) } returns duplikatIm.convert()
            every { mockImRepo.oppdaterMedBeriketDokument(any(), any(), any()) } just Runs

            val innkommendeMelding = innkommendeMelding(innsendingId, nyInntektsmelding)

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainAllExcludingTempKey
                mapOf(
                    Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                    Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.INNTEKTSMELDING to innkommendeMelding.inntektsmelding.toJson(Inntektsmelding.serializer()),
                            Key.BESTEMMENDE_FRAVAERSDAG to bestemmendeFravaersdag.toJson(),
                            Key.ER_DUPLIKAT_IM to true.toJson(Boolean.serializer()),
                            Key.INNSENDING_ID to innsendingId.toJson(Long.serializer()),
                        ).toJson(),
                )

            verifySequence {
                mockImRepo.hentNyesteInntektsmelding(innkommendeMelding.inntektsmelding.type.id)
            }
            verify(exactly = 0) {
                mockImRepo.oppdaterMedBeriketDokument(any(), any(), any())
            }
        }

        test("håndterer at repo feiler") {
            every {
                mockImRepo.hentNyesteInntektsmelding(any())
            } throws RuntimeException("thank you, next")

            val innkommendeMelding = innkommendeMelding(innsendingId)

            val forventetFail =
                Fail(
                    feilmelding = "Klarte ikke lagre inntektsmelding i database.",
                    event = innkommendeMelding.eventName,
                    transaksjonId = innkommendeMelding.transaksjonId,
                    forespoerselId = innkommendeMelding.inntektsmelding.type.id,
                    utloesendeMelding = innkommendeMelding.toMap().toJson(),
                )

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainAllExcludingTempKey forventetFail.tilMelding()

            verifySequence {
                mockImRepo.hentNyesteInntektsmelding(any())
            }
            verify(exactly = 0) {
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
                    mockImRepo.hentNyesteInntektsmelding(any())
                    mockImRepo.oppdaterMedBeriketDokument(any(), any(), any())
                }
            }
        }
    })

private val bestemmendeFravaersdag = 20.oktober

private fun innkommendeMelding(
    innsendingId: Long,
    inntektsmelding: Inntektsmelding = mockInntektsmeldingV1(),
): LagreImMelding =
    LagreImMelding(
        eventName = EventName.INNTEKTSMELDING_SKJEMA_LAGRET,
        behovType = BehovType.LAGRE_IM,
        transaksjonId = UUID.randomUUID(),
        data =
            mapOf(
                Key.INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
                Key.BESTEMMENDE_FRAVAERSDAG to bestemmendeFravaersdag.toJson(),
                Key.INNSENDING_ID to innsendingId.toJson(Long.serializer()),
            ),
        inntektsmelding = inntektsmelding,
        bestemmendeFravaersdag = bestemmendeFravaersdag,
        innsendingId = innsendingId,
    )

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
