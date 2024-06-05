package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.every
import io.mockk.mockk
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic

fun redisPollerMedMockClient(answerOnAttemptNo: Int, answer: String): RedisPoller {
    val answers = List(answerOnAttemptNo - 1) { null }.plus(answer)

    val command = mockk<RedisCommands<String, String>> {
        every { this@mockk.get(any()) } returnsMany answers
    }

    val connection = mockk<StatefulRedisConnection<String, String>>(relaxed = true) {
        every { sync() } returns command
    }

    val redisClient = mockk<RedisClient> {
        every { connect() } returns connection
    }

    return mockStatic(RedisClient::class) {
        every { RedisClient.create(any<String>()) } returns redisClient
        RedisPoller()
    }
}
