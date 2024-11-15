package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart

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

class ForespoerselBesvartRiverTest :
    FunSpec({
        val testRapid = TestRapid()

        ForespoerselBesvartRiver().connect(testRapid)

        beforeEach {
            testRapid.reset()
            clearAllMocks()
        }

        test("Ved notis om besvart forespørsel publiseres behov om å hente notifikasjon-ID-er _uten_ IM-ID fra Spinn") {
            val forespoerselId = UUID.randomUUID()
            val forventetPublisert =
                mapOf(
                    Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
                    Key.UUID to UUID.randomUUID().toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                        ).toJson(),
                )

            testRapid.sendJson(
                Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART.toJson(Pri.NotisType.serializer()),
                Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            )

            testRapid.inspektør.size shouldBeExactly 1

            val publisert = testRapid.firstMessage().toMap()

            publisert shouldContainKey Key.UUID

            publisert.minus(Key.UUID) shouldContainAllExcludingTempKey forventetPublisert.minus(Key.UUID)
        }

        test("Ved notis om besvart forespørsel publiseres behov om å hente notifikasjon-ID-er _med_ IM-ID fra Spinn") {
            val forespoerselId = UUID.randomUUID()
            val spinnInntektsmeldingId = UUID.randomUUID()
            val forventetPublisert =
                mapOf(
                    Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
                    Key.UUID to UUID.randomUUID().toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                            Key.SPINN_INNTEKTSMELDING_ID to spinnInntektsmeldingId.toJson(),
                        ).toJson(),
                )

            testRapid.sendJson(
                Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART.toJson(Pri.NotisType.serializer()),
                Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Pri.Key.SPINN_INNTEKTSMELDING_ID to spinnInntektsmeldingId.toJson(),
            )

            testRapid.inspektør.size shouldBeExactly 1

            val publisert = testRapid.firstMessage().toMap()

            publisert shouldContainKey Key.UUID

            publisert.minus(Key.UUID) shouldContainAllExcludingTempKey forventetPublisert.minus(Key.UUID)
        }
    })
