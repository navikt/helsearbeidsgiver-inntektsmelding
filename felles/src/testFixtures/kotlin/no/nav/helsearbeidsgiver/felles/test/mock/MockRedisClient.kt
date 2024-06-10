package no.nav.helsearbeidsgiver.felles.test.mock

import io.lettuce.core.KeyValue
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisConnection
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic

fun redisWithMockRedisClient(mockStorageInit: Map<String, String?>): RedisConnection {
    val mockCommands = mockk<RedisCommands<String, String>>().setupMock(mockStorageInit)
    return mockStatic(RedisClient::class) {
        every { RedisClient.create(any<String>()) } returns mockRedisClient(mockCommands)
        RedisConnection("")
    }
}

fun mockRedisClient(commands: RedisCommands<String, String>): RedisClient {
    val connection = mockk<StatefulRedisConnection<String, String>>(relaxed = true) {
        every { sync() } returns commands
    }

    return mockk<RedisClient> {
        every { connect() } returns connection
    }
}

fun RedisCommands<String, String>.setupMock(mockStorageInit: Map<String, String?>): RedisCommands<String, String> =
    also {
        val mockStorage = mockStorageInit.toMutableMap()

        val key = slot<String>()
        val value = slot<String>()

        // Fungerer som en capture slot for vararg
        val varargKeys = mutableListOf<String>()

        every { this@setupMock.get(capture(key)) } answers {
            mockStorage[key.captured]
        }

        every { mget(*varargAll { varargKeys.add(it) }) } answers {
            val keyValuePairs = varargKeys.associateWith { mockStorage[it] }
                .map { KeyValue.fromNullable(it.key, it.value) }

            varargKeys.clear()

            keyValuePairs
        }

        every { setex(capture(key), any(), capture(value)) } answers {
            mockStorage[key.captured] = value.captured
            "OK"
        }
    }
