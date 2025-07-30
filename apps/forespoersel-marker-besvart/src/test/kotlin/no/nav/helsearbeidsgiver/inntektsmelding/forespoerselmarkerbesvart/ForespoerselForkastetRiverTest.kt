package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmarkerbesvart

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.mockk.clearAllMocks
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.kafka.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rr.test.firstMessage
import no.nav.helsearbeidsgiver.felles.rr.test.mockConnectToRapid
import no.nav.helsearbeidsgiver.felles.rr.test.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class ForespoerselForkastetRiverTest :
    FunSpec({
        val testRapid = TestRapid()

        mockConnectToRapid(testRapid) {
            listOf(
                ForespoerselForkastetRiver(),
            )
        }

        beforeEach {
            testRapid.reset()
            clearAllMocks()
        }

        test("Ved notis om forkastet forespørsel publiseres event om forkastet forespørsel") {
            val forespoerselId = UUID.randomUUID()
            val forventetPublisert =
                mapOf(
                    Key.EVENT_NAME to EventName.FORESPOERSEL_FORKASTET.toJson(),
                    Key.KONTEKST_ID to UUID.randomUUID().toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                )

            testRapid.sendJson(
                Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_FORKASTET.toJson(Pri.NotisType.serializer()),
                Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            )

            testRapid.inspektør.size shouldBeExactly 1

            val publisert = testRapid.firstMessage().toMap()

            publisert shouldContainKey Key.KONTEKST_ID

            publisert.minus(Key.KONTEKST_ID) shouldContainExactly forventetPublisert.minus(Key.KONTEKST_ID)
        }
    })
