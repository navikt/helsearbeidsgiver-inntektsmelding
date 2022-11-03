package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.every
import io.mockk.mockk

fun mockRedisPoller(answers: List<String>): RedisPoller {
    val command = mockk<RedisCommands<String, String>> {
        every { this@mockk.get(any()) } returnsMany answers
    }

    val connection = mockk<StatefulRedisConnection<String, String>> {
        every { sync() } returns command
        every { use {} } returns Unit
    }

    val redisClient = mockk<RedisClient> {
        every { connect() } returns connection
    }

    return mockStatic(RedisClient::class) {
        every { RedisClient.create(any<String>()) } returns redisClient
        RedisPoller()
    }
}
