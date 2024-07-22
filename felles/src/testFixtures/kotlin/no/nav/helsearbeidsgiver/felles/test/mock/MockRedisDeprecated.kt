package no.nav.helsearbeidsgiver.felles.test.mock

import io.lettuce.core.RedisClient
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStoreDeprecated
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic

class MockRedisDeprecated {
    val store = mockk<RedisStoreDeprecated>()

    private val mockStorage = mutableMapOf<RedisKey, String>()

    private val redisKey = slot<RedisKey>()
    private val redisKeys = slot<Set<RedisKey>>()
    private val newValue = slot<String>()

    init {
        setup()
    }

    fun setup() {
        mockStorage.clear()

        every { store.set(capture(redisKey), capture(newValue)) } answers {
            mockStorage[redisKey.captured] = newValue.captured
        }

        every { store.get(capture(redisKey)) } answers {
            mockStorage[redisKey.captured]
        }

        every { store.getAll(capture(redisKeys)) } answers {
            redisKeys.captured
                .associate {
                    it.toString() to mockStorage[it]
                }.mapValuesNotNull { it }
        }
    }
}

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
