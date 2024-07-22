package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.mockEksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.MockHentIm.toMap
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import java.util.UUID

class HentLagretImRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockImRepo = mockk<InntektsmeldingRepository>()

        HentLagretImRiver(mockImRepo).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        context("henter inntektsmelding") {
            withData(
                mapOf(
                    "kun inntektsmelding" to Pair(mockInntektsmelding(), null),
                    "kun ekstern inntektsmelding" to Pair(null, mockEksternInntektsmelding()),
                    "ingen funnet" to Pair(null, null),
                    "begge typer funnet (skal ikke skje)" to Pair(mockInntektsmelding(), mockEksternInntektsmelding()),
                ),
            ) { lagret ->
                every {
                    mockImRepo.hentNyesteEksternEllerInternInntektsmelding(any())
                } returns lagret

                val innkommendeMelding = MockHentIm.innkommendeMelding()

                testRapid.sendJson(
                    innkommendeMelding.toMap(),
                )

                testRapid.inspektør.size shouldBeExactly 1

                testRapid.firstMessage().toMap() shouldContainExactly
                    mapOf(
                        Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                        Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                        Key.DATA to
                            innkommendeMelding.data
                                .plus(
                                    mapOf(
                                        Key.LAGRET_INNTEKTSMELDING to
                                            lagret.first
                                                ?.toJson(Inntektsmelding.serializer())
                                                ?.toSuccessJson()
                                                .orDefault(MockHentIm.tomResultJson()),
                                        Key.EKSTERN_INNTEKTSMELDING to
                                            lagret.second
                                                ?.toJson(EksternInntektsmelding.serializer())
                                                ?.toSuccessJson()
                                                .orDefault(MockHentIm.tomResultJson()),
                                    ),
                                ).toJson(),
                    )

                verifySequence {
                    mockImRepo.hentNyesteEksternEllerInternInntektsmelding(innkommendeMelding.forespoerselId)
                }
            }
        }

        test("håndterer feil") {
            every {
                mockImRepo.hentNyesteEksternEllerInternInntektsmelding(any())
            } throws NullPointerException()

            val innkommendeMelding = MockHentIm.innkommendeMelding()

            val forventetFail =
                Fail(
                    feilmelding = "Klarte ikke hente inntektsmelding fra database.",
                    event = innkommendeMelding.eventName,
                    transaksjonId = innkommendeMelding.transaksjonId,
                    forespoerselId = innkommendeMelding.forespoerselId,
                    utloesendeMelding = innkommendeMelding.toMap().toJson(),
                )

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()

            verifySequence {
                mockImRepo.hentNyesteEksternEllerInternInntektsmelding(innkommendeMelding.forespoerselId)
            }
        }

        context("ignorerer melding") {
            withData(
                mapOf(
                    "melding med ukjent behov" to Pair(Key.BEHOV, BehovType.TILGANGSKONTROLL.toJson()),
                    "melding med data som flagg" to Pair(Key.DATA, "".toJson()),
                    "melding med fail" to Pair(Key.FAIL, MockHentIm.fail.toJson(Fail.serializer())),
                ),
            ) { uoensketKeyMedVerdi ->
                testRapid.sendJson(
                    MockHentIm
                        .innkommendeMelding()
                        .toMap()
                        .plus(uoensketKeyMedVerdi),
                )

                testRapid.inspektør.size shouldBeExactly 0

                verify(exactly = 0) {
                    mockImRepo.hentNyesteEksternEllerInternInntektsmelding(any())
                }
            }
        }
    })

private object MockHentIm {
    fun innkommendeMelding(): HentLagretImMelding {
        val forespoerselId = UUID.randomUUID()

        return HentLagretImMelding(
            eventName = EventName.KVITTERING_REQUESTED,
            behovType = BehovType.HENT_LAGRET_IM,
            transaksjonId = UUID.randomUUID(),
            data =
                mapOf(
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                ),
            forespoerselId = forespoerselId,
        )
    }

    fun HentLagretImMelding.toMap() =
        mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to behovType.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to data.toJson(),
        )

    fun tomResultJson(): JsonElement = ResultJson().toJson(ResultJson.serializer())

    val fail =
        Fail(
            feilmelding = "Filthy, little hobbitses...",
            event = EventName.INNTEKTSMELDING_MOTTATT,
            transaksjonId = UUID.randomUUID(),
            forespoerselId = UUID.randomUUID(),
            utloesendeMelding = JsonNull,
        )
}

private fun JsonElement.toSuccessJson(): JsonElement = ResultJson(success = this).toJson(ResultJson.serializer())
