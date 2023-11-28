package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.verify
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedis
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.test.date.januar

class SpinnServiceTest : FunSpec({
    val testRapid = TestRapid()

    val mockRedis = MockRedis()

    SpinnService(testRapid, mockRedis.store)

    beforeEach {
        testRapid.reset()
        clearAllMocks()
        mockRedis.setup()
    }

    test("Publiser nytt behov med inntektsmeldingId ved nytt event") {

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.EKSTERN_INNTEKTSMELDING_REQUESTED.toJson(),
            Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
            Key.UUID to Mock.transaksjonsId.toJson(),
            Key.SPINN_INNTEKTSMELDING_ID to Mock.spinnInntektsmeldingId.toJson()
        )

        val actual = testRapid.firstMessage().toMap()

        testRapid.inspektør.size shouldBeExactly 1
        Key.BEHOV.les(BehovType.serializer(), actual) shouldBe BehovType.HENT_EKSTERN_INNTEKTSMELDING
        Key.SPINN_INNTEKTSMELDING_ID.les(UuidSerializer, actual) shouldBe Mock.spinnInntektsmeldingId
    }

    test("EksternInntektsmelding blir skrevet til redis") {

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.EKSTERN_INNTEKTSMELDING_REQUESTED.toJson(),
            Key.DATA to "".toJson(),
            Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
            Key.UUID to Mock.transaksjonsId.toJson(),
            Key.SPINN_INNTEKTSMELDING_ID to Mock.spinnInntektsmeldingId.toJson(),
            Key.EKSTERN_INNTEKTSMELDING to Mock.eksternInntektsmelding.toJson(EksternInntektsmelding.serializer())
        )

        verify {
            mockRedis.store.set(
                RedisKey.of(Mock.transaksjonsId, Key.EKSTERN_INNTEKTSMELDING),
                Mock.eksternInntektsmelding.toJsonStr(EksternInntektsmelding.serializer())
            )
        }
    }
})

private object Mock {
    val transaksjonsId = randomUuid()
    val forespoerselId = randomUuid()
    val spinnInntektsmeldingId = randomUuid()

    val eksternInntektsmelding = EksternInntektsmelding(
        "AltinnPortal",
        "1.438",
        "AR123456",
        1.januar(2020).atStartOfDay()
    )
}
