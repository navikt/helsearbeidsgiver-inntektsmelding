package no.nav.helsearbeidsgiver.inntektsmelding.innsending.api

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.api.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rr.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.felles.rr.test.message
import no.nav.helsearbeidsgiver.felles.rr.test.mockConnectToRapid
import no.nav.helsearbeidsgiver.felles.rr.test.sendJson
import no.nav.helsearbeidsgiver.felles.test.json.lesBehov
import no.nav.helsearbeidsgiver.felles.test.json.plusData
import no.nav.helsearbeidsgiver.felles.test.mock.mockFail
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespoersel
import no.nav.helsearbeidsgiver.felles.test.mock.mockInnsending
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.kl
import java.util.UUID

class ApiInnsendingServiceTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockRedisStore = mockk<RedisStore>(relaxed = true)

        mockConnectToRapid(testRapid) {
            listOf(
                ServiceRiverStateless(
                    ApiInnsendingService(it, mockRedisStore),
                ),
            )
        }

        beforeEach {
            testRapid.reset()
            clearAllMocks()
        }

        test("nytt inntektsmeldingskjema lagres og sendes videre til beriking") {
            val kontekstId = UUID.randomUUID()
            val innsending = Mock.innsending
            testRapid.sendJson(
                Mock.steg0(kontekstId),
            )

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.message(0).lesBehov() shouldBe BehovType.HENT_TRENGER_IM

            testRapid.sendJson(
                Mock.steg1(kontekstId).plusData(
                    Key.INNSENDING to innsending.toJson(Innsending.serializer()),
                ),
            )

            testRapid.inspektør.size shouldBeExactly 2
            testRapid.message(1).lesBehov() shouldBe BehovType.LAGRE_IM_SKJEMA

            testRapid.sendJson(
                Mock.steg2(kontekstId).plusData(
                    Key.SKJEMA_INNTEKTSMELDING to innsending.skjema.toJson(SkjemaInntektsmelding.serializer()),
                ),
            )

            testRapid.inspektør.size shouldBeExactly 3
            testRapid.message(2).toMap().also {
                Key.EVENT_NAME.lesOrNull(EventName.serializer(), it) shouldBe EventName.INNTEKTSMELDING_SKJEMA_LAGRET
                Key.KONTEKST_ID.lesOrNull(UuidSerializer, it) shouldBe kontekstId

                val data = it[Key.DATA]?.toMap().orEmpty()
                Key.SKJEMA_INNTEKTSMELDING.lesOrNull(SkjemaInntektsmelding.serializer(), data) shouldBe innsending.skjema
            }

            verify {
                mockRedisStore.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success = innsending.skjema.forespoerselId.toJson(),
                    ),
                )
            }
        }

        test("duplikat skjema sendes _ikke_ videre til beriking") {
            val kontekstId = UUID.randomUUID()
            val innsending = mockInnsending()

            testRapid.sendJson(
                Mock.steg0(kontekstId),
            )

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.message(0).lesBehov() shouldBe BehovType.HENT_TRENGER_IM

            testRapid.sendJson(
                Mock.steg1(kontekstId).plusData(
                    Key.INNSENDING to innsending.toJson(Innsending.serializer()),
                ),
            )

            testRapid.inspektør.size shouldBeExactly 2
            testRapid.message(1).lesBehov() shouldBe BehovType.LAGRE_IM_SKJEMA

            testRapid.sendJson(
                Mock.steg2(kontekstId).plusData(
                    Key.ER_DUPLIKAT_IM to true.toJson(Boolean.serializer()),
                ),
            )

            testRapid.inspektør.size shouldBeExactly 2

            verify {
                mockRedisStore.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success =
                            Mock.innsending.skjema.forespoerselId
                                .toJson(),
                    ),
                )
            }
        }

        test("svar med feilmelding ved feil") {
            val fail =
                mockFail(
                    feilmelding = "Databasen er smekk full.",
                    eventName = EventName.API_INNSENDING_STARTET,
                    behovType = BehovType.HENT_TRENGER_IM,
                )

            testRapid.sendJson(Mock.steg0(fail.kontekstId))

            testRapid.sendJson(fail.tilMelding())

            testRapid.inspektør.size shouldBeExactly 1

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
    val innsending = mockInnsending()
    val mottatt = 15.august.kl(12, 0, 0, 0)

    fun steg0(kontekstId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.API_INNSENDING_STARTET.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.INNSENDING to innsending.toJson(Innsending.serializer()),
                    Key.MOTTATT to mottatt.toJson(),
                ).toJson(),
        )

    fun steg1(kontekstId: UUID): Map<Key, JsonElement> =
        steg0(kontekstId).plusData(
            Key.FORESPOERSEL_SVAR to mockForespoersel().toJson(Forespoersel.serializer()),
        )

    fun steg2(kontekstId: UUID): Map<Key, JsonElement> =
        steg1(kontekstId).plusData(
            mapOf(
                Key.SKJEMA_INNTEKTSMELDING to innsending.skjema.toJson(SkjemaInntektsmelding.serializer()),
                Key.INNTEKTSMELDING_ID to innsending.innsendingId.toJson(),
                Key.ER_DUPLIKAT_IM to false.toJson(Boolean.serializer()),
            ),
        )
}
