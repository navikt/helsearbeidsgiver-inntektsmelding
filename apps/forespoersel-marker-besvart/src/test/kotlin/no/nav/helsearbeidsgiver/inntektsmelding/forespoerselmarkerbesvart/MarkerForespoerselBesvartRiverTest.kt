package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmarkerbesvart

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
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
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.felles.test.mock.mockFail
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class MarkerForespoerselBesvartRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockPriProducer = mockk<PriProducer>()

        MarkerForespoerselBesvartRiver(mockPriProducer).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        test("Ved event om mottatt inntektsmelding på rapid-topic publiseres notis om å markere forespørsel som besvart på pri-topic") {
            // Må bare returnere en Result med gyldig JSON
            every { mockPriProducer.send(*anyVararg<Pair<Pri.Key, JsonElement>>()) } returns Result.success(JsonNull)

            val expectedForespoerselId = UUID.randomUUID()

            testRapid.sendJson(
                Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
                Key.KONTEKST_ID to UUID.randomUUID().toJson(),
                Key.DATA to
                    mapOf(
                        Key.FORESPOERSEL_ID to expectedForespoerselId.toJson(),
                    ).toJson(),
            )

            testRapid.inspektør.size shouldBeExactly 0

            verifySequence {
                mockPriProducer.send(
                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART_SIMBA.toJson(Pri.NotisType.serializer()),
                    Pri.Key.FORESPOERSEL_ID to expectedForespoerselId.toJson(),
                )
            }
        }

        context("ignorerer melding") {
            withData(
                mapOf(
                    "melding med behov" to Pair(Key.BEHOV, BehovType.HENT_PERSONER.toJson()),
                    "melding med fail" to Pair(Key.FAIL, mockFail.toJson(Fail.serializer())),
                ),
            ) { uoensketKeyMedVerdi ->
                testRapid.sendJson(
                    Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
                    Key.KONTEKST_ID to UUID.randomUUID().toJson(),
                    Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
                    uoensketKeyMedVerdi,
                )

                testRapid.inspektør.size shouldBeExactly 0

                verify(exactly = 0) {
                    mockPriProducer.send(*anyVararg<Pair<Pri.Key, JsonElement>>())
                }
            }
        }
    })

private val mockFail = mockFail("Life, eh, finds a way.", EventName.INNTEKTSMELDING_MOTTATT)
