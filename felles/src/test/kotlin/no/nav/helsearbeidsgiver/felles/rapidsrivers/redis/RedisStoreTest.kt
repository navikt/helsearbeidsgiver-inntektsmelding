package no.nav.helsearbeidsgiver.felles.rapidsrivers.redis

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.test.mock.redisWithMockRedisClient
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class RedisStoreTest :
    FunSpec({

        test(RedisStore::get.name) {
            val keyPrefix = RedisPrefix.HentForespoersel
            val transaksjonId = UUID.randomUUID()

            val redisStore =
                RedisStore(
                    redis =
                        redisWithMockRedisClient(
                            mockStorageInit =
                                mapOf(
                                    "$keyPrefix#$transaksjonId" to "\"mango\"",
                                    "$keyPrefix#$transaksjonId#Feilmelding" to "\"papaya\"",
                                    "$keyPrefix#$transaksjonId#${Key.FNR}" to "\"kokosnøtt\"",
                                    "$keyPrefix#$transaksjonId#${Key.TILGANG}" to null,
                                ),
                        ),
                    keyPrefix = keyPrefix,
                )

            mapOf(
                RedisKey.of(transaksjonId) to "mango",
                RedisKey.feilmelding(transaksjonId) to "papaya",
                RedisKey.of(transaksjonId, Key.FNR) to "kokosnøtt",
            ).forEach { (key, expected) ->
                redisStore.get(key)?.fromJson(String.serializer()) shouldBe expected
            }

            listOf(
                RedisKey.of(transaksjonId, Key.TILGANG),
                RedisKey.of(UUID.randomUUID(), Key.FNR),
            ).forEach { key ->
                redisStore.get(key)?.fromJson(String.serializer()).shouldBeNull()
            }
        }

        test(RedisStore::getAll.name) {
            val keyPrefix = RedisPrefix.Spinn
            val transaksjonId = UUID.randomUUID()

            val redisStore =
                RedisStore(
                    redis =
                        redisWithMockRedisClient(
                            mockStorageInit =
                                mapOf(
                                    "$keyPrefix#$transaksjonId" to "\"eple\"",
                                    "$keyPrefix#$transaksjonId#Feilmelding" to "\"appelsin\"",
                                    "$keyPrefix#$transaksjonId#${Key.FNR}" to "\"banan\"",
                                    "$keyPrefix#$transaksjonId#${Key.TILGANG}" to null,
                                ),
                        ),
                    keyPrefix = keyPrefix,
                )

            val keysWithValues =
                setOf(
                    RedisKey.of(transaksjonId),
                    RedisKey.feilmelding(transaksjonId),
                    RedisKey.of(transaksjonId, Key.FNR),
                )

            redisStore.getAll(keysWithValues) shouldContainExactly
                mapOf(
                    "$transaksjonId" to "eple".toJson(),
                    "$transaksjonId#Feilmelding" to "appelsin".toJson(),
                    "$transaksjonId#${Key.FNR}" to "banan".toJson(),
                )

            val keysWithoutValues =
                setOf(
                    RedisKey.of(transaksjonId, Key.TILGANG),
                    RedisKey.of(UUID.randomUUID(), Key.FNR),
                )

            redisStore.getAll(keysWithoutValues).shouldBeEmpty()
        }

        test(RedisStore::set.name) {
            val keyPrefix = RedisPrefix.TilgangOrg
            val transaksjonId = UUID.randomUUID()

            val redisStore =
                RedisStore(
                    redis =
                        redisWithMockRedisClient(
                            mockStorageInit =
                                mapOf(
                                    "$keyPrefix#$transaksjonId" to "\"durian\"",
                                    "$keyPrefix#$transaksjonId#${Key.TILGANG}" to null,
                                ),
                        ),
                    keyPrefix = keyPrefix,
                )

            val keys =
                setOf(
                    RedisKey.of(transaksjonId),
                    RedisKey.of(transaksjonId, Key.TILGANG),
                )

            redisStore.getAll(keys) shouldContainExactly
                mapOf(
                    "$transaksjonId" to "durian".toJson(),
                )

            redisStore.set(RedisKey.of(transaksjonId), "rabarbra".toJson())

            redisStore.getAll(keys) shouldContainExactly
                mapOf(
                    "$transaksjonId" to "rabarbra".toJson(),
                )

            redisStore.set(RedisKey.of(transaksjonId, Key.TILGANG), "dragefrukt".toJson())

            redisStore.getAll(keys) shouldContainExactly
                mapOf(
                    "$transaksjonId" to "rabarbra".toJson(),
                    "$transaksjonId#${Key.TILGANG}" to "dragefrukt".toJson(),
                )
        }
    })
