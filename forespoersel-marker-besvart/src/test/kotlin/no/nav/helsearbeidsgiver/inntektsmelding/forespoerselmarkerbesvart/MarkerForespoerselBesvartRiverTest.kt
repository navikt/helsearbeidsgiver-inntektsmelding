package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmarkerbesvart

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
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
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
                Key.UUID to UUID.randomUUID().toJson(),
                Key.FORESPOERSEL_ID to expectedForespoerselId.toJson(),
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
                    "melding med data" to Pair(Key.DATA, mapOf(Key.FNR to Fnr.genererGyldig().toJson()).toJson()),
                    "melding med fail" to Pair(Key.FAIL, mockFail.toJson(Fail.serializer())),
                ),
            ) { uoensketKeyMedVerdi ->
                testRapid.sendJson(
                    Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
                    Key.UUID to UUID.randomUUID().toJson(),
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

private val mockFail =
    Fail(
        feilmelding = "Life, eh, finds a way.",
        event = EventName.INNTEKTSMELDING_MOTTATT,
        transaksjonId = UUID.randomUUID(),
        forespoerselId = UUID.randomUUID(),
        utloesendeMelding = JsonNull,
    )
