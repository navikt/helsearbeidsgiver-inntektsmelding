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
import no.nav.hag.simba.kontrakt.domene.inntektsmelding.LagretInntektsmelding
import no.nav.hag.simba.kontrakt.domene.inntektsmelding.test.mockEksternInntektsmelding
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.test.mock.mockFail
import no.nav.hag.simba.utils.felles.test.mock.mockSkjemaInntektsmelding
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.test.firstMessage
import no.nav.hag.simba.utils.rr.test.mockConnectToRapid
import no.nav.hag.simba.utils.rr.test.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.river.MockHentIm.toMap
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.april
import java.util.UUID

class HentLagretImRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockImRepo = mockk<InntektsmeldingRepository>()

        mockConnectToRapid(testRapid) {
            listOf(
                HentLagretImRiver(mockImRepo),
            )
        }

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        context("henter inntektsmelding") {
            withData(
                mapOf(
                    "kun skjema (med navn)" to LagretInntektsmelding.Skjema("Nifs Krumkake", mockSkjemaInntektsmelding(), 12.april.atStartOfDay()),
                    "kun skjema (uten navn)" to LagretInntektsmelding.Skjema(null, mockSkjemaInntektsmelding(), 12.april.atStartOfDay()),
                    "kun ekstern inntektsmelding" to LagretInntektsmelding.Ekstern(mockEksternInntektsmelding()),
                    "ingen funnet" to null,
                ),
            ) { lagret ->
                every { mockImRepo.hentNyesteInntektsmelding(any()) } returns lagret

                val innkommendeMelding = MockHentIm.innkommendeMelding()

                testRapid.sendJson(
                    innkommendeMelding.toMap(),
                )

                testRapid.inspektør.size shouldBeExactly 1

                testRapid.firstMessage().toMap() shouldContainExactly
                    mapOf(
                        Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                        Key.KONTEKST_ID to innkommendeMelding.kontekstId.toJson(),
                        Key.DATA to
                            innkommendeMelding.data
                                .plus(
                                    Key.LAGRET_INNTEKTSMELDING to
                                        ResultJson(
                                            success = lagret?.toJson(LagretInntektsmelding.serializer()),
                                        ).toJson(),
                                ).toJson(),
                    )

                verifySequence {
                    mockImRepo.hentNyesteInntektsmelding(innkommendeMelding.forespoerselId)
                }
            }
        }

        test("håndterer feil") {
            every {
                mockImRepo.hentNyesteInntektsmelding(any())
            } throws NullPointerException()

            val innkommendeMelding = MockHentIm.innkommendeMelding()

            val forventetFail =
                Fail(
                    feilmelding = "Klarte ikke hente inntektsmelding fra database.",
                    kontekstId = innkommendeMelding.kontekstId,
                    utloesendeMelding = innkommendeMelding.toMap(),
                )

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()

            verifySequence {
                mockImRepo.hentNyesteInntektsmelding(innkommendeMelding.forespoerselId)
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
                    mockImRepo.hentNyesteInntektsmelding(any())
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
            kontekstId = UUID.randomUUID(),
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
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to data.toJson(),
        )

    val fail = mockFail("Filthy, little hobbitses...", EventName.INNTEKTSMELDING_MOTTATT)
}
