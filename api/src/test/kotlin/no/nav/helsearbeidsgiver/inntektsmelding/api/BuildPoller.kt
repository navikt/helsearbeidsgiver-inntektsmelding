package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.every
import io.mockk.mockk

fun buildPoller(answers: List<String>): RedisPoller {
    val connection = mockk<StatefulRedisConnection<String, String>>()
    val command = mockk<RedisCommands<String, String>>()

    every {
        command.get(any())
    } returnsMany answers

    every {
        connection.sync()
    } returns command

    every {
        connection.use {}
    } returns Unit

    val redisClient = mockk<RedisClient>()
    every {
        redisClient.connect()
    } returns connection

    return RedisPoller(redisClient)
}
