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
    private val newValue = slot<String>()

    // Fungerer som en capture slot for vararg
    private val keysToCheck = mutableListOf<RedisKey>()

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

        every { store.exist(*varargAll { keysToCheck.add(it) }) } answers {
            val allKeys = mockStorage.keys.toSet()

            val keysExist = keysToCheck.intersect(allKeys).size.toLong()

            keysToCheck.clear()

            keysExist
        }
    }
}
