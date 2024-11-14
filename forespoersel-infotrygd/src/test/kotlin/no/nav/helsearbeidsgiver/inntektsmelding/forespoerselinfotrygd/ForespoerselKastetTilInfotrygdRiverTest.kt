package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselinfotrygd

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainKey
import io.mockk.clearAllMocks
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.felles.test.shouldContainAllExcludingTempKey
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class ForespoerselKastetTilInfotrygdRiverTest :
    FunSpec({
        val testRapid = TestRapid()

        ForespoerselKastetTilInfotrygdRiver().connect(testRapid)

        beforeEach {
            testRapid.reset()
            clearAllMocks()
        }

        test("Ved notis om forespørsel kastet til Infotrygd publiseres event om forespørsel kastet til Infotrygd") {
            val forespoerselId = UUID.randomUUID()
            val forventetPublisert =
                mapOf(
                    Key.EVENT_NAME to EventName.FORESPOERSEL_KASTET_TIL_INFOTRYGD.toJson(),
                    Key.UUID to UUID.randomUUID().toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                )

            testRapid.sendJson(
                Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_KASTET_TIL_INFOTRYGD.toJson(Pri.NotisType.serializer()),
                Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            )

            testRapid.inspektør.size shouldBeExactly 1

            val publisert = testRapid.firstMessage().toMap()

            publisert shouldContainKey Key.UUID

            publisert.minus(Key.UUID) shouldContainAllExcludingTempKey forventetPublisert.minus(Key.UUID)
        }
    })
