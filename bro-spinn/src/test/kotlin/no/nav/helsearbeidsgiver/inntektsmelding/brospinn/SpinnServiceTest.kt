package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class SpinnServiceTest :
    FunSpec({
        val testRapid = TestRapid()

        ServiceRiverStateless(
            SpinnService(testRapid),
        ).connect(testRapid)

        beforeEach {
            testRapid.reset()
            clearAllMocks()
        }

        test("Publiser nytt behov med inntektsmeldingId ved nytt event") {

            testRapid.sendJson(
                Key.EVENT_NAME to EventName.EKSTERN_INNTEKTSMELDING_REQUESTED.toJson(),
                Key.UUID to Mock.transaksjonId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
                        Key.SPINN_INNTEKTSMELDING_ID to Mock.spinnInntektsmeldingId.toJson(),
                    ).toJson(),
            )

            val actual = testRapid.firstMessage().toMap()
            val actualData = actual[Key.DATA].shouldNotBeNull().toMap()

            testRapid.inspektør.size shouldBeExactly 1
            Key.BEHOV.les(BehovType.serializer(), actual) shouldBe BehovType.HENT_EKSTERN_INNTEKTSMELDING
            Key.SPINN_INNTEKTSMELDING_ID.les(UuidSerializer, actualData) shouldBe Mock.spinnInntektsmeldingId
        }
    })

private object Mock {
    val transaksjonId: UUID = UUID.randomUUID()
    val forespoerselId: UUID = UUID.randomUUID()
    val spinnInntektsmeldingId: UUID = UUID.randomUUID()
}
