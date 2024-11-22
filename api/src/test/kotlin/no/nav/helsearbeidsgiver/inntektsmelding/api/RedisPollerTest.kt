package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class RedisPollerTest :
    FunSpec({
        // For å skippe kall til 'delay'
        coroutineTestScope = true

        val mockRedisStore = mockk<RedisStore>()
        val redisPoller = RedisPoller(mockRedisStore)

        val key = UUID.randomUUID()
        val etSlagsResultat = ResultJson(success = "noe data".toJson())

        beforeEach {
            clearAllMocks()
        }

        test("skal finne med tillatt antall forsøk") {
            every { mockRedisStore.lesResultat(any()) } returnsMany answers(answerOnAttemptNo = 10, answer = etSlagsResultat)

            val json = redisPoller.hent(key)

            json shouldBe etSlagsResultat
        }

        test("skal ikke finne etter maks forsøk") {
            every { mockRedisStore.lesResultat(any()) } returnsMany answers(answerOnAttemptNo = 11, answer = etSlagsResultat)

            assertThrows<RedisPollerTimeoutException> {
                redisPoller.hent(key)
            }
        }
    })

private fun answers(
    answerOnAttemptNo: Int,
    answer: ResultJson,
): List<ResultJson?> = List(answerOnAttemptNo - 1) { null }.plus(answer)
