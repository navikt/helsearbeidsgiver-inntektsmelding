package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import io.kotest.core.spec.style.FunSpec
import io.kotest.data.row
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
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.mockEksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.brospinn.MockHent.toMap
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class HentEksternImRiverTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockSpinnKlient = mockk<SpinnKlient>()

        HentEksternImRiver(mockSpinnKlient).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        test("henter ekstern inntektsmelding") {
            val innkommendeMelding = MockHent.innkommendeMelding()

            every { mockSpinnKlient.hentEksternInntektsmelding(any()) } returns mockEksternInntektsmelding()

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                    Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                    Key.DATA to
                        innkommendeMelding.data
                            .plus(
                                Key.EKSTERN_INNTEKTSMELDING to mockEksternInntektsmelding().toJson(EksternInntektsmelding.serializer()),
                            ).toJson(),
                )

            verifySequence {
                mockSpinnKlient.hentEksternInntektsmelding(innkommendeMelding.spinnImId)
            }
        }

        context("håndterer feil") {
            withData(
                mapOf(
                    "spinn-api feil" to
                        row(
                            SpinnApiException("You spin me round."),
                            "Klarte ikke hente ekstern inntektsmelding via Spinn API: You spin me round.",
                        ),
                    "ukjent feil" to
                        row(
                            IllegalArgumentException("Hæ, Dead Or Alive?"),
                            "Ukjent feil under henting av ekstern inntektsmelding via Spinn API.",
                        ),
                ),
            ) { (error, expectedFeilmelding) ->
                val innkommendeMelding = MockHent.innkommendeMelding()

                val innkommendeJsonMap = innkommendeMelding.toMap()

                val forventetFail =
                    Fail(
                        feilmelding = expectedFeilmelding,
                        event = innkommendeMelding.eventName,
                        transaksjonId = innkommendeMelding.transaksjonId,
                        forespoerselId = innkommendeMelding.forespoerselId,
                        utloesendeMelding = innkommendeJsonMap.toJson(),
                    )

                every { mockSpinnKlient.hentEksternInntektsmelding(any()) } throws error

                testRapid.sendJson(innkommendeJsonMap)

                testRapid.inspektør.size shouldBeExactly 1

                testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()

                verifySequence {
                    mockSpinnKlient.hentEksternInntektsmelding(innkommendeMelding.spinnImId)
                }
            }
        }

        context("ignorerer melding") {
            withData(
                mapOf(
                    "melding med uønsket behov" to Pair(Key.BEHOV, BehovType.HENT_VIRKSOMHET_NAVN.toJson()),
                    "melding med data som flagg" to Pair(Key.DATA, "".toJson()),
                    "melding med fail" to Pair(Key.FAIL, MockHent.fail.toJson(Fail.serializer())),
                ),
            ) { uoensketKeyMedVerdi ->
                testRapid.sendJson(
                    MockHent
                        .innkommendeMelding()
                        .toMap()
                        .plus(uoensketKeyMedVerdi),
                )

                testRapid.inspektør.size shouldBeExactly 0

                verify(exactly = 0) {
                    mockSpinnKlient.hentEksternInntektsmelding(any())
                }
            }
        }
    })

private object MockHent {
    fun innkommendeMelding(): HentEksternImMelding {
        val forespoerselId = UUID.randomUUID()
        val spinnImId = UUID.randomUUID()

        return HentEksternImMelding(
            eventName = EventName.EKSTERN_INNTEKTSMELDING_REQUESTED,
            behovType = BehovType.HENT_EKSTERN_INNTEKTSMELDING,
            transaksjonId = UUID.randomUUID(),
            data =
                mapOf(
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.SPINN_INNTEKTSMELDING_ID to spinnImId.toJson(),
                ),
            forespoerselId = forespoerselId,
            spinnImId = spinnImId,
        )
    }

    fun HentEksternImMelding.toMap(): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to behovType.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.SPINN_INNTEKTSMELDING_ID to spinnImId.toJson(),
                ).toJson(),
        )

    val fail =
        Fail(
            feilmelding = "Vi spiller ikke Flo Rida sin versjon.",
            event = EventName.EKSTERN_INNTEKTSMELDING_REQUESTED,
            transaksjonId = UUID.randomUUID(),
            forespoerselId = UUID.randomUUID(),
            utloesendeMelding = JsonNull,
        )
}
