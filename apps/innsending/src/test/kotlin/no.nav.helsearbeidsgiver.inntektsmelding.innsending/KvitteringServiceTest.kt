package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.data.row
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.domene.InnsendtInntektsmelding
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.felles.test.json.lesBehov
import no.nav.helsearbeidsgiver.felles.test.mock.mockEksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.mock.mockFail
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
                    mockRedisStore.skrivResultat(transaksjonId, expectedSuccess)
                }
            }
        }

        test("svarer med feilmelding dersom man ikke klarer å hente inntektsmelding") {
            val expectedFailure = MockKvittering.failureResult()

            testRapid.sendJson(
                MockKvittering.steg0(MockKvittering.fail.kontekstId),
            )

            testRapid.sendJson(
                MockKvittering.fail.tilMelding(),
            )

            testRapid.inspektør.size shouldBeExactly 1

            verify {
                mockRedisStore.skrivResultat(MockKvittering.fail.kontekstId, expectedFailure)
            }
        }
    })

private object MockKvittering {
    val foresporselId: UUID = UUID.randomUUID()

    fun steg0(transaksjonId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.KONTEKST_ID to transaksjonId.toJson(),
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
            Key.KONTEKST_ID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to foresporselId.toJson(),
                    Key.LAGRET_INNTEKTSMELDING to
                        ResultJson(
                            success =
                                inntektsmelding?.toJson(Inntektsmelding.serializer()),
                        ).toJson(),
                    Key.EKSTERN_INNTEKTSMELDING to
                        ResultJson(
                            success =
                                eksternInntektsmelding?.toJson(EksternInntektsmelding.serializer()),
                        ).toJson(),
                ).toJson(),
        )

    fun successResult(
        inntektsmelding: Inntektsmelding?,
        eksternInntektsmelding: EksternInntektsmelding?,
    ): ResultJson =
        ResultJson(
            success = InnsendtInntektsmelding(inntektsmelding, eksternInntektsmelding).toJson(InnsendtInntektsmelding.serializer()),
        )

    fun failureResult(): ResultJson =
        ResultJson(
            failure = fail.feilmelding.toJson(),
        )

    val fail = mockFail("Fool of a Took!", EventName.KVITTERING_REQUESTED)
}
