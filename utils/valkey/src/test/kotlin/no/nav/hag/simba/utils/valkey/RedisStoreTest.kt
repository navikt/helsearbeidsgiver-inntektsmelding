package no.nav.hag.simba.utils.valkey

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.valkey.test.redisWithMockRedisClient
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class RedisStoreTest :
    FunSpec({

        context(RedisStore::lesAlleMellomlagrede.name) {
            test("leser mellomlagrede OK") {
                val keyPrefix = RedisPrefix.Kvittering
                val kontekstId = UUID.randomUUID()

                val redisStore =
                    RedisStore(
                        redis =
                            redisWithMockRedisClient(
                                mockStorageInit =
                                    mapOf(
                                        "$keyPrefix#$kontekstId" to "\"mango\"",
                                        "$keyPrefix#$kontekstId#${Key.FNR}" to "\"ananas\"",
                                        "$keyPrefix#$kontekstId#${Key.ORGNR_UNDERENHET}" to "\"kokosnøtt\"",
                                        "$keyPrefix#$kontekstId#${Key.TILGANG}" to null,
                                    ),
                            ),
                        keyPrefix = keyPrefix,
                    )

                redisStore.lesAlleMellomlagrede(UUID.randomUUID()).shouldBeEmpty()

                redisStore.lesAlleMellomlagrede(kontekstId) shouldContainExactly
                    mapOf(
                        Key.FNR to "ananas".toJson(),
                        Key.ORGNR_UNDERENHET to "kokosnøtt".toJson(),
                    )
            }

            test("ignorerer nøkler som ikke er av typen 'Key'") {
                val keyPrefix = RedisPrefix.AktiveOrgnr
                val kontekstId = UUID.randomUUID()

                val redisStore =
                    RedisStore(
                        redis =
                            redisWithMockRedisClient(
                                mockStorageInit =
                                    mapOf(
                                        "" to "\"jordbær\"",
                                        "${Key.FNR}" to "\"bringebær\"",
                                        "$kontekstId#${Key.FNR}" to "\"blåbær\"",
                                        "$keyPrefix#$kontekstId#ikkeEnKey" to "\"tyttebær\"",
                                        "$keyPrefix#$kontekstId#${Key.ORGNR_UNDERENHET}" to "\"kokosnøtt\"",
                                    ),
                            ),
                        keyPrefix = keyPrefix,
                    )

                redisStore.lesAlleMellomlagrede(UUID.randomUUID()).shouldBeEmpty()

                redisStore.lesAlleMellomlagrede(kontekstId) shouldContainExactly
                    mapOf(
                        Key.ORGNR_UNDERENHET to "kokosnøtt".toJson(),
                    )
            }

            test("ignorerer verdier som ikke er gyldig JSON") {
                val keyPrefix = RedisPrefix.TilgangForespoersel
                val kontekstId = UUID.randomUUID()

                val redisStore =
                    RedisStore(
                        redis =
                            redisWithMockRedisClient(
                                mockStorageInit =
                                    mapOf(
                                        "$keyPrefix#$kontekstId#${Key.FNR}" to "\"ananas\"",
                                        "$keyPrefix#$kontekstId#${Key.ORGNR_UNDERENHET}" to "streng uten ekstra fnutter",
                                        "$keyPrefix#$kontekstId#${Key.PERSONER}" to "{true}",
                                        "$keyPrefix#$kontekstId#${Key.VIRKSOMHETER}" to "]]42[[",
                                    ),
                            ),
                        keyPrefix = keyPrefix,
                    )

                redisStore.lesAlleMellomlagrede(UUID.randomUUID()).shouldBeEmpty()

                redisStore.lesAlleMellomlagrede(kontekstId) shouldContainExactly
                    mapOf(
                        Key.FNR to "ananas".toJson(),
                    )
            }
        }

        context(RedisStore::lesResultat.name) {
            test("leser resultat OK") {
                val keyPrefix = RedisPrefix.HentForespoersel
                val kontekstId = UUID.randomUUID()

                val redisStore =
                    RedisStore(
                        redis =
                            redisWithMockRedisClient(
                                mockStorageInit =
                                    mapOf(
                                        "$kontekstId" to "\"banan\"",
                                        "$keyPrefix#$kontekstId" to "{\"success\":\"mango\"}",
                                        "$keyPrefix#$kontekstId#${Key.FNR}" to "\"ananas\"",
                                    ),
                            ),
                        keyPrefix = keyPrefix,
                    )

                redisStore.lesResultat(UUID.randomUUID()).shouldBeNull()

                redisStore.lesResultat(kontekstId)?.success to "mango"
            }

            test("feiler ved ugyldig type") {
                val keyPrefix = RedisPrefix.HentForespoersel
                val kontekstId = UUID.randomUUID()

                val redisStore =
                    RedisStore(
                        redis =
                            redisWithMockRedisClient(
                                mockStorageInit =
                                    mapOf(
                                        "$keyPrefix#$kontekstId" to "\"mango\"",
                                    ),
                            ),
                        keyPrefix = keyPrefix,
                    )

                shouldThrow<SerializationException> {
                    redisStore.lesResultat(kontekstId)?.success to "mango"
                }
            }
        }

        test(RedisStore::skrivMellomlagring.name) {
            val keyPrefix = RedisPrefix.TilgangOrg
            val kontekstId = UUID.randomUUID()

            val redisStore =
                RedisStore(
                    redis =
                        redisWithMockRedisClient(
                            mockStorageInit =
                                mapOf(
                                    "$keyPrefix#$kontekstId" to "{\"success\":\"rabarbra\"}",
                                ),
                        ),
                    keyPrefix = keyPrefix,
                )

            redisStore.lesAlleMellomlagrede(kontekstId).shouldBeEmpty()

            redisStore.skrivMellomlagring(kontekstId, Key.FNR, "durian".toJson())

            redisStore.lesAlleMellomlagrede(kontekstId) shouldContainExactly
                mapOf(
                    Key.FNR to "durian".toJson(),
                )

            redisStore.skrivMellomlagring(kontekstId, Key.INNTEKT, "kiwi".toJson())
            redisStore.skrivMellomlagring(kontekstId, Key.JOURNALPOST_ID, "litchi".toJson())

            redisStore.lesAlleMellomlagrede(kontekstId) shouldContainExactly
                mapOf(
                    Key.FNR to "durian".toJson(),
                    Key.INNTEKT to "kiwi".toJson(),
                    Key.JOURNALPOST_ID to "litchi".toJson(),
                )

            redisStore.skrivMellomlagring(kontekstId, Key.INNTEKT, "granateple".toJson())

            redisStore.lesAlleMellomlagrede(kontekstId) shouldContainExactly
                mapOf(
                    Key.FNR to "durian".toJson(),
                    Key.INNTEKT to "granateple".toJson(),
                    Key.JOURNALPOST_ID to "litchi".toJson(),
                )

            redisStore.lesAlleMellomlagrede(UUID.randomUUID()).shouldBeEmpty()

            // Har ikke blitt overskrevet
            redisStore.lesResultat(kontekstId)?.success shouldBe "rabarbra".toJson()
        }

        test(RedisStore::skrivResultat.name) {
            val keyPrefix = RedisPrefix.Inntekt
            val kontekstId1 = UUID.randomUUID()
            val kontekstId2 = UUID.randomUUID()

            val redisStore =
                RedisStore(
                    redis =
                        redisWithMockRedisClient(
                            mockStorageInit =
                                mapOf(
                                    "$keyPrefix#$kontekstId1#${Key.FNR}" to "\"durian\"",
                                    "$keyPrefix#$kontekstId2" to "{\"failure\":\"rambutan\"}",
                                ),
                        ),
                    keyPrefix = keyPrefix,
                )

            redisStore.lesResultat(UUID.randomUUID()).shouldBeNull()
            redisStore.lesResultat(kontekstId1).shouldBeNull()
            redisStore.lesResultat(kontekstId2)?.failure shouldBe "rambutan".toJson()
            redisStore.lesResultat(kontekstId2)?.success.shouldBeNull()

            redisStore.skrivResultat(kontekstId1, ResultJson(success = "rabarbra".toJson()))

            redisStore.lesResultat(UUID.randomUUID()).shouldBeNull()
            redisStore.lesResultat(kontekstId1)?.failure.shouldBeNull()
            redisStore.lesResultat(kontekstId1)?.success shouldBe "rabarbra".toJson()

            redisStore.skrivResultat(kontekstId1, ResultJson(success = "en kolossal rabarbra".toJson()))

            redisStore.lesResultat(kontekstId1)?.failure.shouldBeNull()
            redisStore.lesResultat(kontekstId1)?.success shouldBe "en kolossal rabarbra".toJson()

            redisStore.skrivResultat(kontekstId1, ResultJson(failure = "en megaloman rabarbra".toJson()))

            redisStore.lesResultat(kontekstId1)?.failure shouldBe "en megaloman rabarbra".toJson()
            redisStore.lesResultat(kontekstId1)?.success.shouldBeNull()

            // Har ikke blitt overskrevet
            redisStore.lesAlleMellomlagrede(kontekstId1) shouldContainExactly mapOf(Key.FNR to "durian".toJson())
            redisStore.lesResultat(kontekstId2)?.failure shouldBe "rambutan".toJson()
        }
    })
