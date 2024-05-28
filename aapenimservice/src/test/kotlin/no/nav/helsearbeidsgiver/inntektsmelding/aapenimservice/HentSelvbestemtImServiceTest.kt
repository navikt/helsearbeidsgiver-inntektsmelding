package no.nav.helsearbeidsgiver.inntektsmelding.aapenimservice

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.verify
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedis
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import java.util.UUID

class HentSelvbestemtImServiceTest : FunSpec({

    val testRapid = TestRapid()
    val mockRedis = MockRedis()

    HentSelvbestemtImService(testRapid, mockRedis.store)

    beforeEach {
        testRapid.reset()
        clearAllMocks()
        mockRedis.setup()
    }

    test("hent inntektsmelding") {
        val clientId = UUID.randomUUID()
        val transaksjonId = UUID.randomUUID()

        mockStatic(::randomUuid) {
            every { randomUuid() } returns transaksjonId

            testRapid.sendJson(
                mockStartMelding(clientId, transaksjonId)
            )
        }

        testRapid.inspektør.size shouldBeExactly 1
        testRapid.firstMessage().lesBehov() shouldBe BehovType.HENT_SELVBESTEMT_IM

        testRapid.sendJson(
            mockDataMelding(transaksjonId)
        )

        testRapid.inspektør.size shouldBeExactly 1

        verify {
            mockRedis.store.set(
                RedisKey.of(clientId),
                ResultJson(
                    success = Mock.inntektsmelding.toJson(Inntektsmelding.serializer())
                ).toJsonStr()
            )
        }
    }

    test("svar med feilmelding ved uhåndterbare feil") {
        val clientId = UUID.randomUUID()
        val transaksjonId = UUID.randomUUID()
        val feilmelding = "Snitches get stitches (fordi vi har gratis helsevesen)"

        mockStatic(::randomUuid) {
            every { randomUuid() } returns transaksjonId

            testRapid.sendJson(
                mockStartMelding(clientId, transaksjonId)
            )
        }

        testRapid.sendJson(
            Fail(
                feilmelding = feilmelding,
                event = EventName.SELVBESTEMT_IM_REQUESTED,
                transaksjonId = transaksjonId,
                forespoerselId = null,
                utloesendeMelding = JsonObject(
                    mapOf(
                        Key.BEHOV.toString() to BehovType.HENT_SELVBESTEMT_IM.toJson()
                    )
                )
            ).tilMelding()
        )

        testRapid.inspektør.size shouldBeExactly 1
        testRapid.firstMessage().lesBehov() shouldBe BehovType.HENT_SELVBESTEMT_IM

        verify {
            mockRedis.store.set(
                RedisKey.of(clientId),
                ResultJson(
                    failure = feilmelding.toJson()
                ).toJsonStr()
            )
        }
    }
})

fun mockStartMelding(clientId: UUID, transaksjonId: UUID): Map<Key, JsonElement> =
    mapOf(
        Key.EVENT_NAME to EventName.SELVBESTEMT_IM_REQUESTED.toJson(),
        Key.CLIENT_ID to clientId.toJson(),
        Key.UUID to transaksjonId.toJson(),
        Key.SELVBESTEMT_ID to Mock.selvbestemtId.toJson()
    )

fun mockDataMelding(transaksjonId: UUID): Map<Key, JsonElement> =
    mapOf(
        Key.EVENT_NAME to EventName.SELVBESTEMT_IM_REQUESTED.toJson(),
        Key.UUID to transaksjonId.toJson(),
        Key.SELVBESTEMT_ID to Mock.selvbestemtId.toJson(),
        Key.DATA to "".toJson(),
        Key.SELVBESTEMT_INNTEKTSMELDING to Mock.inntektsmelding.toJson(Inntektsmelding.serializer())
    )

private fun JsonElement.lesBehov(): BehovType? =
    Key.BEHOV.lesOrNull(BehovType.serializer(), this.toMap())

private object Mock {
    val selvbestemtId: UUID = UUID.randomUUID()
    val inntektsmelding = mockInntektsmeldingV1()
}
