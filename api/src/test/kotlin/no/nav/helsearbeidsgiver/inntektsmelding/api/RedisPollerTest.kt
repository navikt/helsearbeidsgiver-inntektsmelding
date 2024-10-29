package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.ForespurtData
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespoersel
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class RedisPollerTest :
    FunSpec({
        // For å skippe kall til 'delay'
        coroutineTestScope = true

        val mockRedisStore = mockk<RedisStore>()
        val redisPoller = RedisPoller(mockRedisStore)

        val key = UUID.randomUUID()
        val dataJson = "noe data".toJson()

        beforeEach {
            clearAllMocks()
        }

        test("skal finne med tillatt antall forsøk") {
            every { mockRedisStore.lesResultat(any()) } returnsMany answers(answerOnAttemptNo = 10, answer = dataJson)

            val json = redisPoller.hent(key)

            json shouldBe dataJson
        }

        test("skal ikke finne etter maks forsøk") {
            every { mockRedisStore.lesResultat(any()) } returnsMany answers(answerOnAttemptNo = 11, answer = dataJson)

            assertThrows<RedisPollerTimeoutException> {
                redisPoller.hent(key)
            }
        }

        test("skal parse forespurt data korrekt") {
            val expected = mockForespoersel()
            val expectedJson =
                """
                {
                    "type": "${expected.type}",
                    "orgnr": "${expected.orgnr}",
                    "fnr": "${expected.fnr}",
                    "vedtaksperiodeId": ${expected.vedtaksperiodeId.toJson()},
                    "sykmeldingsperioder": ${expected.sykmeldingsperioder.toJsonStr(Periode.serializer().list())},
                    "egenmeldingsperioder": ${expected.egenmeldingsperioder.toJsonStr(Periode.serializer().list())},
                    "bestemmendeFravaersdager": ${expected.bestemmendeFravaersdager.toJsonStr(MapSerializer(String.serializer(), LocalDateSerializer))},
                    "forespurtData": ${expected.forespurtData.toJsonStr(ForespurtData.serializer())},
                    "erBesvart": ${expected.erBesvart}
                }
                """.removeJsonWhitespace()
                    .parseJson()

            every { mockRedisStore.lesResultat(any()) } returnsMany answers(answerOnAttemptNo = 1, answer = expectedJson)

            val resultat = redisPoller.hent(key).fromJson(Forespoersel.serializer())

            resultat shouldBe expected
        }
    })

private fun answers(
    answerOnAttemptNo: Int,
    answer: JsonElement,
): List<JsonElement?> = List(answerOnAttemptNo - 1) { null }.plus(answer)
