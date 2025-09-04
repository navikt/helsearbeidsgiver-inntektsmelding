package no.nav.hag.simba.utils.valkey.test

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import no.nav.hag.simba.utils.valkey.RedisConnection
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic

class MockRedis(
    keyPrefix: RedisPrefix,
) {
    private val mockCommands = mockk<RedisCommands<String, String>>()
    private val redis =
        mockStatic(RedisClient::class) {
            every { RedisClient.create(any<RedisURI>()) } returns mockRedisClient(mockCommands)
            RedisConnection("host", 0, "username", "password")
        }

    val store: RedisStore

    init {
        setup()

        store =
            spyk(
                RedisStore(
                    redis = redis,
                    keyPrefix = keyPrefix,
                ),
            )
    }

    fun setup() {
        mockCommands.setupMock(emptyMap())
    }
}
