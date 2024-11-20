package no.nav.helsearbeidsgiver.inntektsmelding.inntektselvbestemtservice

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Inntekt
import no.nav.helsearbeidsgiver.felles.domene.InntektPerMaaned
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.felles.test.json.lesBehov
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
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

        ServiceRiverStateless(
            InntektSelvbestemtService(testRapid, mockRedisStore),
        ).connect(testRapid)

        beforeEach {
            testRapid.reset()
            clearAllMocks()
        }

        test("hent inntekt") {
            val transaksjonId = UUID.randomUUID()

            testRapid.sendJson(
                Mock.melding(transaksjonId, Mock.steg0Data),
            )

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.firstMessage().lesBehov() shouldBe BehovType.HENT_INNTEKT

            testRapid.sendJson(
                Mock.melding(transaksjonId, Mock.steg1Data),
            )

            testRapid.inspektør.size shouldBeExactly 1

            verify {
                mockRedisStore.skrivResultat(
                    transaksjonId,
                    ResultJson(
                        success = Mock.inntekt.toJson(Inntekt.serializer()),
                    ),
                )
            }
        }

        test("svar med feilmelding ved uhåndterbare feil") {
            val transaksjonId = UUID.randomUUID()
            val feilmelding = "Teknisk feil, prøv igjen senere."

            testRapid.sendJson(
                Mock.melding(transaksjonId, Mock.steg0Data),
            )

            testRapid.sendJson(
                Fail(
                    feilmelding = feilmelding,
                    event = EventName.INNTEKT_SELVBESTEMT_REQUESTED,
                    transaksjonId = transaksjonId,
                    forespoerselId = null,
                    utloesendeMelding =
                        JsonObject(
                            mapOf(
                                Key.BEHOV.toString() to BehovType.HENT_INNTEKT.toJson(),
                            ),
                        ),
                ).tilMelding(),
            )

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.firstMessage().lesBehov() shouldBe BehovType.HENT_INNTEKT

            verify {
                mockRedisStore.skrivResultat(
                    transaksjonId,
                    ResultJson(
                        failure = feilmelding.toJson(),
                    ),
                )
            }
        }
    })

private object Mock {
    val inntekt =
        Inntekt(
            listOf(
                InntektPerMaaned(april(2019), 40000.0),
                InntektPerMaaned(mai(2019), 42000.0),
                InntektPerMaaned(juni(2019), 44000.0),
            ),
        )

    val steg0Data =
        mapOf(
            Key.ORGNRUNDERENHET to Orgnr.genererGyldig().toJson(),
            Key.FNR to Fnr.genererGyldig().toJson(),
            Key.INNTEKTSDATO to 14.april.toJson(),
        )

    val steg1Data =
        steg0Data.plus(
            Key.INNTEKT to inntekt.toJson(Inntekt.serializer()),
        )

    fun melding(
        transaksjonId: UUID,
        data: Map<Key, JsonElement>,
    ): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.INNTEKT_SELVBESTEMT_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to data.toJson(),
        )
}
