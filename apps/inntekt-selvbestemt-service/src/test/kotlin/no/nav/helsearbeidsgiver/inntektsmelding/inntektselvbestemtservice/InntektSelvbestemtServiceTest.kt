package no.nav.helsearbeidsgiver.inntektsmelding.inntektselvbestemtservice

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
import no.nav.hag.simba.utils.felles.json.inntektMapSerializer
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.test.json.lesBehov
import no.nav.hag.simba.utils.felles.test.json.plusData
import no.nav.hag.simba.utils.felles.test.mock.mockFail
import no.nav.hag.simba.utils.rr.service.ServiceRiverStateless
import no.nav.hag.simba.utils.rr.test.firstMessage
import no.nav.hag.simba.utils.rr.test.mockConnectToRapid
import no.nav.hag.simba.utils.rr.test.sendJson
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.juni
import no.nav.helsearbeidsgiver.utils.test.date.mai
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class InntektSelvbestemtServiceTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockRedisStore = mockk<RedisStore>(relaxed = true)

        mockConnectToRapid(testRapid) {
            listOf(
                ServiceRiverStateless(
                    InntektSelvbestemtService(it, mockRedisStore),
                ),
            )
        }

        beforeEach {
            testRapid.reset()
            clearAllMocks()
        }

        test("hent inntekt") {
            val kontekstId = UUID.randomUUID()

            testRapid.sendJson(
                Mock.steg0(kontekstId),
            )

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.firstMessage().lesBehov() shouldBe BehovType.HENT_INNTEKT

            testRapid.sendJson(
                Mock.steg1(kontekstId),
            )

            testRapid.inspektør.size shouldBeExactly 1

            verify {
                mockRedisStore.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success = Mock.inntekt.toJson(inntektMapSerializer),
                    ),
                )
            }
        }

        test("svar med feilmelding ved uhåndterbare feil") {
            val fail =
                mockFail(
                    feilmelding = "Teknisk feil, prøv igjen senere.",
                    eventName = EventName.SERVICE_HENT_INNTEKT_SELVBESTEMT,
                    behovType = BehovType.HENT_INNTEKT,
                )

            testRapid.sendJson(
                Mock.steg0(fail.kontekstId),
            )

            testRapid.sendJson(fail.tilMelding())

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.firstMessage().lesBehov() shouldBe BehovType.HENT_INNTEKT

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
    private val orgnr = Orgnr.genererGyldig()
    private val fnr = Fnr.genererGyldig()
    val inntekt =
        mapOf(
            april(2019) to 40000.0,
            mai(2019) to 42000.0,
            juni(2019) to 44000.0,
        )

    fun steg0(kontekstId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.INNTEKT_SELVBESTEMT_REQUESTED.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.ORGNR_UNDERENHET to orgnr.toJson(),
                    Key.FNR to fnr.toJson(),
                    Key.INNTEKTSDATO to 14.april.toJson(),
                ).toJson(),
        )

    fun steg1(kontekstId: UUID) =
        steg0(kontekstId)
            .plus(Key.EVENT_NAME to EventName.SERVICE_HENT_INNTEKT_SELVBESTEMT.toJson())
            .plusData(
                Key.INNTEKT to inntekt.toJson(inntektMapSerializer),
            )
}
