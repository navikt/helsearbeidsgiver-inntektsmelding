package no.nav.helsearbeidsgiver.inntektsmelding.selvbestemthentimservice

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.felles.test.json.lesBehov
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class HentSelvbestemtImServiceTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockRedisStore = mockk<RedisStore>(relaxed = true)

        ServiceRiverStateless(
            HentSelvbestemtImService(testRapid, mockRedisStore),
        ).connect(testRapid)

        beforeEach {
            testRapid.reset()
            clearAllMocks()
        }

        test("hent inntektsmelding") {
            val transaksjonId = UUID.randomUUID()

            testRapid.sendJson(
                Mock.startMelding(transaksjonId),
            )

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.firstMessage().lesBehov() shouldBe BehovType.HENT_SELVBESTEMT_IM

            testRapid.sendJson(
                Mock.dataMelding(transaksjonId),
            )

            testRapid.inspektør.size shouldBeExactly 1

            verify {
                mockRedisStore.set(
                    RedisKey.of(transaksjonId),
                    ResultJson(
                        success = Mock.inntektsmelding.toJson(Inntektsmelding.serializer()),
                    ).toJson(ResultJson.serializer()),
                )
            }
        }

        test("svar med feilmelding ved uhåndterbare feil") {
            val transaksjonId = UUID.randomUUID()
            val feilmelding = "Snitches get stitches (fordi vi har gratis helsevesen)"

            testRapid.sendJson(
                Mock.startMelding(transaksjonId),
            )

            testRapid.sendJson(
                Fail(
                    feilmelding = feilmelding,
                    event = EventName.SELVBESTEMT_IM_REQUESTED,
                    transaksjonId = transaksjonId,
                    forespoerselId = null,
                    utloesendeMelding =
                        JsonObject(
                            mapOf(
                                Key.BEHOV.toString() to BehovType.HENT_SELVBESTEMT_IM.toJson(),
                            ),
                        ),
                ).tilMelding(),
            )

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.firstMessage().lesBehov() shouldBe BehovType.HENT_SELVBESTEMT_IM

            verify {
                mockRedisStore.set(
                    RedisKey.of(transaksjonId),
                    ResultJson(
                        failure = feilmelding.toJson(),
                    ).toJson(ResultJson.serializer()),
                )
            }
        }
    })

private object Mock {
    private val selvbestemtId: UUID = UUID.randomUUID()
    val inntektsmelding = mockInntektsmeldingV1()

    fun startMelding(transaksjonId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.SELVBESTEMT_IM_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.SELVBESTEMT_ID to selvbestemtId.toJson(),
                ).toJson(),
        )

    fun dataMelding(transaksjonId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.SELVBESTEMT_IM_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.SELVBESTEMT_ID to selvbestemtId.toJson(),
                    Key.SELVBESTEMT_INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
                ).toJson(),
        )
}
