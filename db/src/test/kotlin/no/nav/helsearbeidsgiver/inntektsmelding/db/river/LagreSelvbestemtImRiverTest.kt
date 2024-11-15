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
import no.nav.helsearbeidsgiver.inntektsmelding.db.SelvbestemtImRepo
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.juli
import java.util.UUID

class LagreSelvbestemtImRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockSelvbestemtImRepo = mockk<SelvbestemtImRepo>()

        LagreSelvbestemtImRiver(mockSelvbestemtImRepo).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        context("selvbestemt inntektsmelding lagres") {
            val inntektsmeldingId = UUID.randomUUID()

            withData(
                mapOf(
                    "hvis ingen andre inntektsmeldinger er mottatt" to null,
                    "hvis ikke duplikat av tidligere inntektsmeldinger" to
                        mockInntektsmeldingV1().copy(
                            id = inntektsmeldingId,
                            sykmeldingsperioder = listOf(13.juli til 31.juli),
                        ),
                ),
            ) { eksisterendeIm ->
                every { mockSelvbestemtImRepo.hentNyesteIm(any()) } returns eksisterendeIm
                every { mockSelvbestemtImRepo.lagreIm(any()) } just Runs

                val nyInntektsmelding = mockInntektsmeldingV1().copy(id = inntektsmeldingId)

                val innkommendeMelding = innkommendeMelding(nyInntektsmelding)

                testRapid.sendJson(innkommendeMelding.toMap())

                testRapid.inspektør.size shouldBeExactly 1

                testRapid.firstMessage().toMap() shouldContainAllExcludingTempKey
                    mapOf(
                        Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                        Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                        Key.DATA to
                            mapOf(
                                Key.SELVBESTEMT_INNTEKTSMELDING to nyInntektsmelding.toJson(Inntektsmelding.serializer()),
                                Key.ER_DUPLIKAT_IM to false.toJson(Boolean.serializer()),
                            ).toJson(),
                    )

                verifySequence {
                    mockSelvbestemtImRepo.hentNyesteIm(nyInntektsmelding.type.id)
                    mockSelvbestemtImRepo.lagreIm(nyInntektsmelding)
                }
            }
        }

        test("duplikat lagres ikke, men svarer OK") {
            val nyInntektsmelding = mockInntektsmeldingV1()

            val duplikatIm =
                nyInntektsmelding.copy(
                    id = UUID.randomUUID(),
                    avsender =
                        nyInntektsmelding.avsender.copy(
                            navn = "Intens Delfia",
                            tlf = "35350404",
                        ),
                    aarsakInnsending = AarsakInnsending.Ny,
                    mottatt = nyInntektsmelding.mottatt.minusDays(14),
                )

            every { mockSelvbestemtImRepo.hentNyesteIm(any()) } returns duplikatIm
            every { mockSelvbestemtImRepo.lagreIm(any()) } just Runs

            val innkommendeMelding = innkommendeMelding(nyInntektsmelding)

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainAllExcludingTempKey
                mapOf(
                    Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                    Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.SELVBESTEMT_INNTEKTSMELDING to nyInntektsmelding.toJson(Inntektsmelding.serializer()),
                            Key.ER_DUPLIKAT_IM to true.toJson(Boolean.serializer()),
                        ).toJson(),
                )

            verifySequence {
                mockSelvbestemtImRepo.hentNyesteIm(nyInntektsmelding.type.id)
            }
            verify(exactly = 0) {
                mockSelvbestemtImRepo.lagreIm(nyInntektsmelding)
            }
        }

        test("håndterer at repo feiler") {
            every {
                mockSelvbestemtImRepo.hentNyesteIm(any())
            } throws RuntimeException("fy fasiken, den svei")

            val innkommendeMelding = innkommendeMelding()

            val forventetFail =
                Fail(
                    feilmelding = "Klarte ikke lagre selvbestemt inntektsmelding i database.",
                    event = innkommendeMelding.eventName,
                    transaksjonId = innkommendeMelding.transaksjonId,
                    forespoerselId = null,
                    utloesendeMelding = innkommendeMelding.toMap().toJson(),
                )

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainAllExcludingTempKey
                forventetFail
                    .tilMelding()
                    .minus(Key.FORESPOERSEL_ID)
                    .plus(
                        Key.SELVBESTEMT_ID to
                            innkommendeMelding.selvbestemtInntektsmelding.type.id
                                .toJson(),
                    )

            verifySequence {
                mockSelvbestemtImRepo.hentNyesteIm(any())
            }
            verify(exactly = 0) {
                mockSelvbestemtImRepo.lagreIm(any())
            }
        }

        context("ignorerer melding") {
            withData(
                mapOf(
                    "melding med uønsket behov" to Pair(Key.BEHOV, BehovType.HENT_VIRKSOMHET_NAVN.toJson()),
                    "melding med data med flagg" to Pair(Key.DATA, "".toJson()),
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
                    mockSelvbestemtImRepo.hentNyesteIm(any())
                    mockSelvbestemtImRepo.lagreIm(any())
                }
            }
        }
    })

private fun innkommendeMelding(selvbestemtInntektsmelding: Inntektsmelding = mockInntektsmeldingV1()): LagreSelvbestemtImMelding =
    LagreSelvbestemtImMelding(
        eventName = EventName.SELVBESTEMT_IM_MOTTATT,
        behovType = BehovType.LAGRE_SELVBESTEMT_IM,
        transaksjonId = UUID.randomUUID(),
        data =
            mapOf(
                Key.SELVBESTEMT_INNTEKTSMELDING to selvbestemtInntektsmelding.toJson(Inntektsmelding.serializer()),
            ),
        selvbestemtInntektsmelding = selvbestemtInntektsmelding,
    )

private fun LagreSelvbestemtImMelding.toMap(): Map<Key, JsonElement> =
    mapOf(
        Key.EVENT_NAME to eventName.toJson(),
        Key.BEHOV to behovType.toJson(),
        Key.UUID to transaksjonId.toJson(),
        Key.DATA to data.toJson(),
    )

private val mockFail =
    Fail(
        feilmelding = "Vi har et KJEMPEPROBLEM!",
        event = EventName.SELVBESTEMT_IM_MOTTATT,
        transaksjonId = UUID.randomUUID(),
        forespoerselId = null,
        utloesendeMelding = JsonNull,
    )
