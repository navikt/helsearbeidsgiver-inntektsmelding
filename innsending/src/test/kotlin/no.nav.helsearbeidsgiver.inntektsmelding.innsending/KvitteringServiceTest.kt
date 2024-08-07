package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import io.kotest.core.spec.style.FunSpec
import io.kotest.data.row
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.InnsendtInntektsmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.felles.test.json.lesBehov
import no.nav.helsearbeidsgiver.felles.test.mock.mockEksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class KvitteringServiceTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockRedisStore = mockk<RedisStore>(relaxed = true)

        ServiceRiverStateless(
            KvitteringService(testRapid, mockRedisStore),
        ).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        context("kvittering hentes") {
            withData(
                mapOf(
                    "inntektsmelding hentes" to row(mockInntektsmelding(), null),
                    "ekstern inntektsmelding hentes" to row(null, mockEksternInntektsmelding()),
                    "ingen inntektsmelding funnet" to row(null, null),
                    "begge typer inntektsmelding funnet (skal ikke skje)" to row(mockInntektsmelding(), mockEksternInntektsmelding()),
                ),
            ) { (expectedInntektsmelding, expectedEksternInntektsmelding) ->
                val transaksjonId: UUID = UUID.randomUUID()
                val expectedSuccess = MockKvittering.successResult(expectedInntektsmelding, expectedEksternInntektsmelding)

                testRapid.sendJson(
                    MockKvittering.steg0(transaksjonId),
                )

                testRapid.inspektør.size shouldBeExactly 1
                testRapid.firstMessage().lesBehov() shouldBe BehovType.HENT_LAGRET_IM

                testRapid.sendJson(
                    MockKvittering.steg1(transaksjonId, expectedInntektsmelding, expectedEksternInntektsmelding),
                )

                testRapid.inspektør.size shouldBeExactly 1

                verify {
                    mockRedisStore.set(RedisKey.of(transaksjonId), expectedSuccess)
                }
            }
        }

        test("svarer med feilmelding dersom man ikke klarer å hente inntektsmelding") {
            val expectedFailure = MockKvittering.failureResult()

            testRapid.sendJson(
                MockKvittering.steg0(MockKvittering.fail.transaksjonId),
            )

            testRapid.sendJson(
                MockKvittering.fail.tilMelding(),
            )

            testRapid.inspektør.size shouldBeExactly 1

            verify {
                mockRedisStore.set(RedisKey.of(MockKvittering.fail.transaksjonId), expectedFailure)
            }
        }
    })

private object MockKvittering {
    val foresporselId: UUID = UUID.randomUUID()

    fun steg0(transaksjonId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to foresporselId.toJson(),
                ).toJson(),
        )

    fun steg1(
        transaksjonId: UUID,
        inntektsmelding: Inntektsmelding?,
        eksternInntektsmelding: EksternInntektsmelding?,
    ): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to foresporselId.toJson(),
                    Key.LAGRET_INNTEKTSMELDING to
                        ResultJson(
                            success =
                                inntektsmelding?.toJson(Inntektsmelding.serializer()),
                        ).toJson(ResultJson.serializer()),
                    Key.EKSTERN_INNTEKTSMELDING to
                        ResultJson(
                            success =
                                eksternInntektsmelding?.toJson(EksternInntektsmelding.serializer()),
                        ).toJson(ResultJson.serializer()),
                ).toJson(),
        )

    fun successResult(
        inntektsmelding: Inntektsmelding?,
        eksternInntektsmelding: EksternInntektsmelding?,
    ): JsonElement =
        ResultJson(
            success = InnsendtInntektsmelding(inntektsmelding, eksternInntektsmelding).toJson(InnsendtInntektsmelding.serializer()),
        ).toJson(ResultJson.serializer())

    fun failureResult(): JsonElement =
        ResultJson(
            failure = fail.feilmelding.toJson(),
        ).toJson(ResultJson.serializer())

    val fail =
        Fail(
            feilmelding = "Fool of a Took!",
            event = EventName.KVITTERING_REQUESTED,
            transaksjonId = UUID.randomUUID(),
            forespoerselId = null,
            utloesendeMelding = JsonNull,
        )
}
