package no.nav.helsearbeidsgiver.inntektsmelding.inntektselvbestemtservice

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.verify
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.InntektPerMaaned
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiver
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedisClassSpecific
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

class InntektSelvbestemtServiceTest : FunSpec({
    val testRapid = TestRapid()
    val mockRedis = MockRedisClassSpecific(RedisPrefix.InntektSelvbestemtService)

    ServiceRiver(
        InntektSelvbestemtService(testRapid, mockRedis.store)
    ).connect(testRapid)

    beforeEach {
        testRapid.reset()
        clearAllMocks()
        mockRedis.setup()
    }

    test("hent inntekt") {
        val transaksjonId = UUID.randomUUID()

        testRapid.sendJson(
            mockStartMelding(transaksjonId)
        )

        testRapid.inspektør.size shouldBeExactly 1
        testRapid.firstMessage().lesBehov() shouldBe BehovType.INNTEKT

        testRapid.sendJson(
            mockDataMelding(transaksjonId)
        )

        testRapid.inspektør.size shouldBeExactly 1

        verify {
            mockRedis.store.set(
                RedisKey.of(transaksjonId),
                ResultJson(
                    success = Mock.inntekt.toJson(Inntekt.serializer())
                ).toJson(ResultJson.serializer())
            )
        }
    }

    test("svar med feilmelding ved uhåndterbare feil") {
        val transaksjonId = UUID.randomUUID()
        val feilmelding = "Teknisk feil, prøv igjen senere."

        testRapid.sendJson(
            mockStartMelding(transaksjonId)
        )

        testRapid.sendJson(
            Fail(
                feilmelding = feilmelding,
                event = EventName.INNTEKT_SELVBESTEMT_REQUESTED,
                transaksjonId = transaksjonId,
                forespoerselId = null,
                utloesendeMelding = JsonObject(
                    mapOf(
                        Key.BEHOV.toString() to BehovType.INNTEKT.toJson()
                    )
                )
            ).tilMelding()
        )

        testRapid.inspektør.size shouldBeExactly 1
        testRapid.firstMessage().lesBehov() shouldBe BehovType.INNTEKT

        verify {
            mockRedis.store.set(
                RedisKey.of(transaksjonId),
                ResultJson(
                    failure = feilmelding.toJson()
                ).toJson(ResultJson.serializer())
            )
        }
    }
})

fun mockStartMelding(transaksjonId: UUID): Map<Key, JsonElement> =
    mapOf(
        Key.EVENT_NAME to EventName.INNTEKT_SELVBESTEMT_REQUESTED.toJson(),
        Key.UUID to transaksjonId.toJson(),
        Key.DATA to "".toJson(),
        Key.FNR to Fnr.genererGyldig().toJson(),
        Key.ORGNRUNDERENHET to Orgnr.genererGyldig().toJson(),
        Key.SKJAERINGSTIDSPUNKT to 14.april.toJson()
    )

fun mockDataMelding(transaksjonId: UUID): Map<Key, JsonElement> =
    mapOf(
        Key.EVENT_NAME to EventName.INNTEKT_SELVBESTEMT_REQUESTED.toJson(),
        Key.UUID to transaksjonId.toJson(),
        Key.DATA to "".toJson(),
        Key.INNTEKT to Mock.inntekt.toJson(Inntekt.serializer())
    )

private fun JsonElement.lesBehov(): BehovType? =
    Key.BEHOV.lesOrNull(BehovType.serializer(), this.toMap())

private object Mock {
    val inntekt = Inntekt(
        listOf(
            InntektPerMaaned(april(2019), 40000.0),
            InntektPerMaaned(mai(2019), 42000.0),
            InntektPerMaaned(juni(2019), 44000.0)
        )
    )
}
