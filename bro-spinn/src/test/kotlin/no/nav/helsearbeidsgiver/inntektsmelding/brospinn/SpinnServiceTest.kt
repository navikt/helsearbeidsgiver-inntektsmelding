package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldNotBeNull
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
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiver
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedisClassSpecific
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.januar
import java.util.UUID

class SpinnServiceTest : FunSpec({
    val testRapid = TestRapid()

    val mockRedis = MockRedisClassSpecific(RedisPrefix.SpinnService)

    ServiceRiver(
        SpinnService(testRapid, mockRedis.store)
    ).connect(testRapid)

    beforeEach {
        testRapid.reset()
        clearAllMocks()
        mockRedis.setup()
    }

    test("Publiser nytt behov med inntektsmeldingId ved nytt event") {

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.EKSTERN_INNTEKTSMELDING_REQUESTED.toJson(),
            Key.UUID to Mock.transaksjonId.toJson(),
            Key.DATA to mapOf(
                Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
                Key.SPINN_INNTEKTSMELDING_ID to Mock.spinnInntektsmeldingId.toJson()
            ).toJson()
        )

        val actual = testRapid.firstMessage().toMap()
        val actualData = actual[Key.DATA].shouldNotBeNull().toMap()

        testRapid.inspektør.size shouldBeExactly 1
        Key.BEHOV.les(BehovType.serializer(), actual) shouldBe BehovType.HENT_EKSTERN_INNTEKTSMELDING
        Key.SPINN_INNTEKTSMELDING_ID.les(UuidSerializer, actualData) shouldBe Mock.spinnInntektsmeldingId
    }

    test("EksternInntektsmelding blir skrevet til redis") {

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.EKSTERN_INNTEKTSMELDING_REQUESTED.toJson(),
            Key.UUID to Mock.transaksjonId.toJson(),
            Key.DATA to mapOf(
                Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
                Key.SPINN_INNTEKTSMELDING_ID to Mock.spinnInntektsmeldingId.toJson(),
                Key.EKSTERN_INNTEKTSMELDING to Mock.eksternInntektsmelding.toJson(EksternInntektsmelding.serializer())
            ).toJson()
        )

        verify {
            mockRedis.store.set(
                RedisKey.of(Mock.transaksjonId, Key.EKSTERN_INNTEKTSMELDING),
                Mock.eksternInntektsmelding.toJson(EksternInntektsmelding.serializer())
            )
        }
    }
})

private object Mock {
    val transaksjonId: UUID = UUID.randomUUID()
    val forespoerselId: UUID = UUID.randomUUID()
    val spinnInntektsmeldingId: UUID = UUID.randomUUID()

    val eksternInntektsmelding = EksternInntektsmelding(
        "AltinnPortal",
        "1.438",
        "AR123456",
        1.januar(2020).atStartOfDay()
    )
}
