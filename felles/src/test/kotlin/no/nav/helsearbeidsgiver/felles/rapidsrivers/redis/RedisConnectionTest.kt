package no.nav.helsearbeidsgiver.felles.rapidsrivers.redis

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.felles.test.mock.redisWithMockRedisClient

class RedisConnectionTest : FunSpec({

    test(RedisConnection::get.name) {
        val redis =
            redisWithMockRedisClient(
                mockStorageInit =
                    mapOf(
                        "atreides" to "good guys",
                        "harkonnen" to null,
                    ),
            )

        redis.get("atreides") shouldBe "good guys"
        redis.get("harkonnen").shouldBeNull()
        redis.get("emperor").shouldBeNull()
    }

    test(RedisConnection::getAll.name) {
        val redis =
            redisWithMockRedisClient(
                mockStorageInit =
                    mapOf(
                        "atreides" to "good guys",
                        "harkonnen" to "bad guys",
                        "bene gesserit" to null,
                    ),
            )

        redis.getAll(
            "atreides",
            "harkonnen",
            "bene gesserit",
            "fremen",
        ) shouldContainExactly
            mapOf(
                "atreides" to "good guys",
                "harkonnen" to "bad guys",
            )
    }

    test(RedisConnection::set.name) {
        val redis =
            redisWithMockRedisClient(
                mockStorageInit = emptyMap(),
            )

        redis.get("paul").shouldBeNull()
        redis.set("paul", "lisan al gaib")
        redis.get("paul") shouldBe "lisan al gaib"
    }
})
