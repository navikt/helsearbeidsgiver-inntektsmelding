package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
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
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Utils.convert
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.mockEksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.mock.mockFail
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.felles.test.mock.mockSkjemaInntektsmelding
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
                    "kun skjema og inntektsmelding" to Triple(mockSkjemaInntektsmelding(), mockInntektsmelding(), null),
                    "kun skjema" to Triple(mockSkjemaInntektsmelding(), null, null),
                    "kun ekstern inntektsmelding" to Triple(null, null, mockEksternInntektsmelding()),
                    "ingen funnet" to Triple(null, null, null),
                    "alle typer funnet (skal ikke skje)" to Triple(mockSkjemaInntektsmelding(), mockInntektsmelding(), mockEksternInntektsmelding()),
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
                        Key.KONTEKST_ID to innkommendeMelding.transaksjonId.toJson(),
                        Key.DATA to
                            innkommendeMelding.data
                                .plus(
                                    mapOf(
                                        Key.SKJEMA_INNTEKTSMELDING to
                                            lagret.first
                                                ?.toJson(SkjemaInntektsmelding.serializer())
                                                ?.toSuccessJson()
                                                .orDefault(MockHentIm.tomResultJson()),
                                        Key.LAGRET_INNTEKTSMELDING to
                                            lagret.second
                                                ?.toJson(Inntektsmelding.serializer())
                                                ?.toSuccessJson()
                                                .orDefault(MockHentIm.tomResultJson()),
                                        Key.EKSTERN_INNTEKTSMELDING to
                                            lagret.third
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

        test("konverterer inntektsmelding til skjema dersom skjema mangler") {
            val innkommendeMelding = MockHentIm.innkommendeMelding()
            val imV1 = mockInntektsmeldingV1()
            val im = imV1.convert()
            val skjema =
                SkjemaInntektsmelding(
                    forespoerselId = innkommendeMelding.forespoerselId,
                    avsenderTlf = imV1.avsender.tlf,
                    agp = imV1.agp,
                    inntekt = imV1.inntekt,
                    refusjon = imV1.refusjon,
                )

            every {
                mockImRepo.hentNyesteEksternEllerInternInntektsmelding(any())
            } returns Triple(null, im, null)

            testRapid.sendJson(
                innkommendeMelding.toMap(),
            )

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                    Key.KONTEKST_ID to innkommendeMelding.transaksjonId.toJson(),
                    Key.DATA to
                        innkommendeMelding.data
                            .plus(
                                mapOf(
                                    Key.SKJEMA_INNTEKTSMELDING to skjema.toJson(SkjemaInntektsmelding.serializer()).toSuccessJson(),
                                    Key.LAGRET_INNTEKTSMELDING to im.toJson(Inntektsmelding.serializer()).toSuccessJson(),
                                    Key.EKSTERN_INNTEKTSMELDING to MockHentIm.tomResultJson(),
                                ),
                            ).toJson(),
                )

            verifySequence {
                mockImRepo.hentNyesteEksternEllerInternInntektsmelding(innkommendeMelding.forespoerselId)
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
                    kontekstId = innkommendeMelding.transaksjonId,
                    utloesendeMelding = innkommendeMelding.toMap(),
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
        val svarKafkaKey = KafkaKey(forespoerselId)

        return HentLagretImMelding(
            eventName = EventName.KVITTERING_REQUESTED,
            behovType = BehovType.HENT_LAGRET_IM,
            transaksjonId = UUID.randomUUID(),
            data =
                mapOf(
                    Key.SVAR_KAFKA_KEY to svarKafkaKey.toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                ),
            svarKafkaKey = svarKafkaKey,
            forespoerselId = forespoerselId,
        )
    }

    fun HentLagretImMelding.toMap() =
        mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to behovType.toJson(),
            Key.KONTEKST_ID to transaksjonId.toJson(),
            Key.DATA to data.toJson(),
        )

    fun tomResultJson(): JsonElement = ResultJson().toJson()

    val fail = mockFail("Filthy, little hobbitses...", EventName.INNTEKTSMELDING_MOTTATT)
}

private fun JsonElement.toSuccessJson(): JsonElement = ResultJson(success = this).toJson()
