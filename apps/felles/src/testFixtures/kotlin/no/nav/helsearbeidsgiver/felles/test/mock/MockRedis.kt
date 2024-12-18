package no.nav.helsearbeidsgiver.felles.test.mock

import io.lettuce.core.RedisClient
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic

class MockRedis(
    keyPrefix: RedisPrefix,
) {
    private val mockCommands = mockk<RedisCommands<String, String>>()
    private val redis =
        mockStatic(RedisClient::class) {
            every { RedisClient.create(any<String>()) } returns mockRedisClient(mockCommands)
            RedisConnection("")
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
