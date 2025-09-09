package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmarkerbesvart

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.mockk.clearAllMocks
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.kontrakt.kafkatopic.pri.Pri
import no.nav.hag.simba.utils.rr.test.firstMessage
import no.nav.hag.simba.utils.rr.test.mockConnectToRapid
import no.nav.hag.simba.utils.rr.test.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class ForespoerselKastetTilInfotrygdRiverTest :
    FunSpec({
        val testRapid = TestRapid()

        mockConnectToRapid(testRapid) {
            listOf(
                ForespoerselKastetTilInfotrygdRiver(),
            )
        }

        beforeEach {
            testRapid.reset()
            clearAllMocks()
        }

        test("Ved notis om forespørsel kastet til Infotrygd publiseres event om forespørsel kastet til Infotrygd") {
            val forespoerselId = UUID.randomUUID()
            val forventetPublisert =
                mapOf(
                    Key.EVENT_NAME to EventName.FORESPOERSEL_KASTET_TIL_INFOTRYGD.toJson(),
                    Key.KONTEKST_ID to UUID.randomUUID().toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                )

            testRapid.sendJson(
                Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_KASTET_TIL_INFOTRYGD.toJson(Pri.NotisType.serializer()),
                Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            )

            testRapid.inspektør.size shouldBeExactly 1

            val publisert = testRapid.firstMessage().toMap()

            publisert shouldContainKey Key.KONTEKST_ID

            publisert.minus(Key.KONTEKST_ID) shouldContainExactly forventetPublisert.minus(Key.KONTEKST_ID)
        }
    })
