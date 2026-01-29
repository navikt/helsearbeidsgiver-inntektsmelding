package no.nav.helsearbeidsgiver.inntektsmelding.selvbestemthentimservice

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.test.json.lesBehov
import no.nav.hag.simba.utils.felles.test.json.plusData
import no.nav.hag.simba.utils.felles.test.mock.mockFail
import no.nav.hag.simba.utils.felles.test.mock.mockInntektsmeldingV1
import no.nav.hag.simba.utils.rr.service.ServiceRiverStateless
import no.nav.hag.simba.utils.rr.test.firstMessage
import no.nav.hag.simba.utils.rr.test.mockConnectToRapid
import no.nav.hag.simba.utils.rr.test.sendJson
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class HentSelvbestemtImServiceTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockRedisStore = mockk<RedisStore>(relaxed = true)

        mockConnectToRapid(testRapid) {
            listOf(
                ServiceRiverStateless(
                    HentSelvbestemtImService(it, mockRedisStore),
                ),
            )
        }

        beforeEach {
            testRapid.reset()
            clearAllMocks()
        }

        test("hent inntektsmelding") {
            val kontekstId = UUID.randomUUID()

            testRapid.sendJson(
                Mock.steg0(kontekstId),
            )

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.firstMessage().lesBehov() shouldBe BehovType.HENT_SELVBESTEMT_IM

            testRapid.sendJson(
                Mock.steg1(kontekstId),
            )

            testRapid.inspektør.size shouldBeExactly 1

            verify {
                mockRedisStore.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success = Mock.inntektsmelding.toJson(Inntektsmelding.serializer()),
                    ),
                )
            }
        }

        test("svar med feilmelding ved uhåndterbare feil") {
            val fail =
                mockFail(
                    feilmelding = "Snitches get stitches (fordi vi har gratis helsevesen)",
                    eventName = EventName.SERVICE_SELVBESTEMT_IM_HENT,
                    behovType = BehovType.HENT_SELVBESTEMT_IM,
                )

            testRapid.sendJson(
                Mock.steg0(fail.kontekstId),
            )

            testRapid.sendJson(fail.tilMelding())

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.firstMessage().lesBehov() shouldBe BehovType.HENT_SELVBESTEMT_IM

            verify {
                mockRedisStore.skrivResultat(
                    fail.kontekstId,
                    ResultJson(
                        failure = fail.feilmelding.toJson(),
                    ),
                )
            }
        }
    })

private object Mock {
    private val selvbestemtId: UUID = UUID.randomUUID()
    val inntektsmelding = mockInntektsmeldingV1()

    fun steg0(kontekstId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.SELVBESTEMT_IM_REQUESTED.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.SELVBESTEMT_ID to selvbestemtId.toJson(),
                ).toJson(),
        )

    fun steg1(kontekstId: UUID): Map<Key, JsonElement> =
        steg0(kontekstId)
            .plus(Key.EVENT_NAME to EventName.SERVICE_SELVBESTEMT_IM_HENT.toJson())
            .plusData(
                Key.SELVBESTEMT_INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
            )
}
