package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class TrengerForespoerselRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockPriProducer = mockk<PriProducer>()

        TrengerForespoerselRiver(mockPriProducer).connect(testRapid)

        test("Ved behov om forespørsel på rapid-topic publiseres behov om forespørsel på pri-topic") {
            // Må bare returnere en Result med gyldig JSON
            every { mockPriProducer.send(*anyVararg<Pair<Pri.Key, JsonElement>>()) } returns Result.success(JsonNull)

            val expectedEvent = EventName.INNTEKT_REQUESTED
            val expectedKontekstId = UUID.randomUUID()
            val expectedForespoerselId = UUID.randomUUID()
            val journalpostId = "denne skal i boomerangen"

            testRapid.sendJson(
                Key.EVENT_NAME to expectedEvent.toJson(),
                Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                Key.KONTEKST_ID to expectedKontekstId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.FORESPOERSEL_ID to expectedForespoerselId.toJson(),
                        Key.JOURNALPOST_ID to journalpostId.toJson(),
                    ).toJson(),
            )

            testRapid.inspektør.size shouldBeExactly 0

            verifySequence {
                mockPriProducer.send(
                    Pri.Key.BEHOV to Pri.BehovType.TRENGER_FORESPØRSEL.toJson(Pri.BehovType.serializer()),
                    Pri.Key.FORESPOERSEL_ID to expectedForespoerselId.toJson(),
                    Pri.Key.BOOMERANG to
                        mapOf(
                            Key.EVENT_NAME to expectedEvent.toJson(),
                            Key.KONTEKST_ID to expectedKontekstId.toJson(),
                            Key.DATA to
                                mapOf(
                                    Key.FORESPOERSEL_ID to expectedForespoerselId.toJson(),
                                    Key.JOURNALPOST_ID to journalpostId.toJson(),
                                ).toJson(),
                        ).toJson(),
                )
            }
        }
    })
