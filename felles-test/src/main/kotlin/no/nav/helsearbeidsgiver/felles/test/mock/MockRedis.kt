package no.nav.helsearbeidsgiver.felles.test.mock

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore

class MockRedis {
    val store = mockk<RedisStore>()

    private val mockStorage = mutableMapOf<RedisKey, String>()

    private val redisKey = slot<RedisKey>()
    private val redisKeys = slot<List<RedisKey>>()
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

        every { store.exist(capture(redisKeys)) } answers {
            mockStorage.keys.intersect(redisKeys.captured.toSet()).size.toLong()
        }
    }
}
