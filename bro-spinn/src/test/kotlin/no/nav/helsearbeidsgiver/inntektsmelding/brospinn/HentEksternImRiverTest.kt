package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.data.row
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.mockEksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.felles.test.shouldContainAllExcludingTempKey
import no.nav.helsearbeidsgiver.inntektsmelding.brospinn.Mock.toMap
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
            val innkommendeMelding = Mock.innkommendeMelding()

            every { mockSpinnKlient.hentEksternInntektsmelding(any()) } returns mockEksternInntektsmelding()

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainAllExcludingTempKey
                mapOf(
                    Key.EVENT_NAME to EventName.EKSTERN_INNTEKTSMELDING_MOTTATT.toJson(),
                    Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FORESPOERSEL_ID to innkommendeMelding.forespoerselId.toJson(),
                            Key.EKSTERN_INNTEKTSMELDING to mockEksternInntektsmelding().toJson(EksternInntektsmelding.serializer()),
                        ).toJson(),
                )

            verifySequence {
                mockSpinnKlient.hentEksternInntektsmelding(innkommendeMelding.spinnImId)
            }
        }

        context("publiserer ikke mottatt-event for ...") {
            withData(
                mapOf(
                    "forespurt inntektsmelding fra nav.no" to "NAV_NO",
                    "selvbestemt inntektsmelding fra nav.no" to "NAV_NO_SELVBESTEMT",
                ),
            ) { avsenderSystemNavn ->
                val imFraNavNo =
                    mockEksternInntektsmelding().copy(
                        avsenderSystemNavn = avsenderSystemNavn,
                    )
                val innkommendeMelding = Mock.innkommendeMelding()

                every { mockSpinnKlient.hentEksternInntektsmelding(any()) } returns imFraNavNo

                testRapid.sendJson(innkommendeMelding.toMap())

                testRapid.inspektør.size shouldBeExactly 0

                verifySequence {
                    mockSpinnKlient.hentEksternInntektsmelding(innkommendeMelding.spinnImId)
                }
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
                val innkommendeMelding = Mock.innkommendeMelding()

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

                testRapid.firstMessage().toMap() shouldContainAllExcludingTempKey forventetFail.tilMelding()

                verifySequence {
                    mockSpinnKlient.hentEksternInntektsmelding(innkommendeMelding.spinnImId)
                }
            }
        }

        context("ignorerer melding") {
            withData(
                mapOf(
                    "melding med uønsket behov" to Pair(Key.BEHOV, BehovType.HENT_VIRKSOMHET_NAVN.toJson()),
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
                    mockSpinnKlient.hentEksternInntektsmelding(any())
                }
            }
        }
    })

private object Mock {
    fun innkommendeMelding(): HentEksternImMelding =
        HentEksternImMelding(
            eventName = EventName.FORESPOERSEL_BESVART,
            transaksjonId = UUID.randomUUID(),
            forespoerselId = UUID.randomUUID(),
            spinnImId = UUID.randomUUID(),
        )

    fun HentEksternImMelding.toMap(): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to eventName.toJson(),
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
            event = EventName.FORESPOERSEL_BESVART,
            transaksjonId = UUID.randomUUID(),
            forespoerselId = UUID.randomUUID(),
            utloesendeMelding = JsonNull,
        )
}
