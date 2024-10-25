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

        context(RedisStore::lesAlleMellomlagrede.name) {
            test("leser OK") {
                val keyPrefix = RedisPrefix.Kvittering
                val transaksjonId = UUID.randomUUID()

                val redisStore =
                    RedisStore(
                        redis =
                            redisWithMockRedisClient(
                                mockStorageInit =
                                    mapOf(
                                        "$keyPrefix#$transaksjonId" to "\"mango\"",
                                        "$keyPrefix#$transaksjonId#Feilmelding" to "\"papaya\"",
                                        "$keyPrefix#$transaksjonId#${Key.FNR}" to "\"ananas\"",
                                        "$keyPrefix#$transaksjonId#${Key.ORGNRUNDERENHET}" to "\"kokosnøtt\"",
                                        "$keyPrefix#$transaksjonId#${Key.TILGANG}" to null,
                                    ),
                            ),
                        keyPrefix = keyPrefix,
                    )

                redisStore.lesAlleMellomlagrede(UUID.randomUUID()).shouldBeEmpty()

                redisStore.lesAlleMellomlagrede(transaksjonId) shouldContainExactly
                    mapOf(
                        Key.FNR to "ananas".toJson(),
                        Key.ORGNRUNDERENHET to "kokosnøtt".toJson(),
                    )
            }

            test("ignorerer nøkler som ikke er av typen 'Key'") {
                val keyPrefix = RedisPrefix.AktiveOrgnr
                val transaksjonId = UUID.randomUUID()

                val redisStore =
                    RedisStore(
                        redis =
                            redisWithMockRedisClient(
                                mockStorageInit =
                                    mapOf(
                                        "" to "\"jordbær\"",
                                        "${Key.FNR}" to "\"bringebær\"",
                                        "$transaksjonId#${Key.FNR}" to "\"blåbær\"",
                                        "$keyPrefix#$transaksjonId#ikkeEnKey" to "\"tyttebær\"",
                                        "$keyPrefix#$transaksjonId#${Key.ORGNRUNDERENHET}" to "\"kokosnøtt\"",
                                    ),
                            ),
                        keyPrefix = keyPrefix,
                    )

                redisStore.lesAlleMellomlagrede(UUID.randomUUID()).shouldBeEmpty()

                redisStore.lesAlleMellomlagrede(transaksjonId) shouldContainExactly
                    mapOf(
                        Key.ORGNRUNDERENHET to "kokosnøtt".toJson(),
                    )
            }

            test("ignorerer verdier som ikke er gyldig JSON") {
                val keyPrefix = RedisPrefix.TilgangForespoersel
                val transaksjonId = UUID.randomUUID()

                val redisStore =
                    RedisStore(
                        redis =
                            redisWithMockRedisClient(
                                mockStorageInit =
                                    mapOf(
                                        "$keyPrefix#$transaksjonId#${Key.FNR}" to "\"ananas\"",
                                        "$keyPrefix#$transaksjonId#${Key.ORGNRUNDERENHET}" to "streng uten ekstra fnutter",
                                        "$keyPrefix#$transaksjonId#${Key.PERSONER}" to "{true}",
                                        "$keyPrefix#$transaksjonId#${Key.VIRKSOMHETER}" to "]]42[[",
                                    ),
                            ),
                        keyPrefix = keyPrefix,
                    )

                redisStore.lesAlleMellomlagrede(UUID.randomUUID()).shouldBeEmpty()

                redisStore.lesAlleMellomlagrede(transaksjonId) shouldContainExactly
                    mapOf(
                        Key.FNR to "ananas".toJson(),
                    )
            }
        }

        test(RedisStore::lesResultat.name) {
            val keyPrefix = RedisPrefix.HentForespoersel
            val transaksjonId = UUID.randomUUID()

            val redisStore =
                RedisStore(
                    redis =
                        redisWithMockRedisClient(
                            mockStorageInit =
                                mapOf(
                                    "$transaksjonId" to "\"banan\"",
                                    "$keyPrefix#$transaksjonId" to "\"mango\"",
                                    "$keyPrefix#$transaksjonId#Feilmelding" to "\"papaya\"",
                                    "$keyPrefix#$transaksjonId#${Key.FNR}" to "\"ananas\"",
                                ),
                        ),
                    keyPrefix = keyPrefix,
                )

            redisStore.lesResultat(UUID.randomUUID()).shouldBeNull()

            redisStore.lesResultat(transaksjonId)?.fromJson(String.serializer()) to "mango"
        }

        test(RedisStore::lesFeil.name) {
            val keyPrefix = RedisPrefix.LagreSelvbestemtIm
            val transaksjonId = UUID.randomUUID()

            val redisStore =
                RedisStore(
                    redis =
                        redisWithMockRedisClient(
                            mockStorageInit =
                                mapOf(
                                    "$transaksjonId" to "\"banan\"",
                                    "$transaksjonId#Feilmelding" to "\"appelsin\"",
                                    "$keyPrefix#$transaksjonId" to "\"mango\"",
                                    "$keyPrefix#$transaksjonId#Feilmelding" to "\"papaya\"",
                                    "$keyPrefix#$transaksjonId#${Key.FNR}" to "\"ananas\"",
                                ),
                        ),
                    keyPrefix = keyPrefix,
                )

            redisStore.lesFeil(UUID.randomUUID()).shouldBeNull()

            redisStore.lesFeil(transaksjonId)?.fromJson(String.serializer()) to "papaya"
        }

        test(RedisStore::skrivMellomlagring.name) {
            val keyPrefix = RedisPrefix.TilgangOrg
            val transaksjonId = UUID.randomUUID()

            val redisStore =
                RedisStore(
                    redis =
                        redisWithMockRedisClient(
                            mockStorageInit =
                                mapOf(
                                    "$keyPrefix#$transaksjonId" to "\"rabarbra\"",
                                    "$keyPrefix#$transaksjonId#Feilmelding" to "\"dragefrukt\"",
                                ),
                        ),
                    keyPrefix = keyPrefix,
                )

            redisStore.lesAlleMellomlagrede(transaksjonId).shouldBeEmpty()

            redisStore.skrivMellomlagring(transaksjonId, Key.FNR, "durian".toJson())

            redisStore.lesAlleMellomlagrede(transaksjonId) shouldContainExactly
                mapOf(
                    Key.FNR to "durian".toJson(),
                )

            redisStore.skrivMellomlagring(transaksjonId, Key.INNTEKT, "kiwi".toJson())
            redisStore.skrivMellomlagring(transaksjonId, Key.JOURNALPOST_ID, "litchi".toJson())

            redisStore.lesAlleMellomlagrede(transaksjonId) shouldContainExactly
                mapOf(
                    Key.FNR to "durian".toJson(),
                    Key.INNTEKT to "kiwi".toJson(),
                    Key.JOURNALPOST_ID to "litchi".toJson(),
                )

            redisStore.skrivMellomlagring(transaksjonId, Key.INNTEKT, "granateple".toJson())

            redisStore.lesAlleMellomlagrede(transaksjonId) shouldContainExactly
                mapOf(
                    Key.FNR to "durian".toJson(),
                    Key.INNTEKT to "granateple".toJson(),
                    Key.JOURNALPOST_ID to "litchi".toJson(),
                )
        }

        test(RedisStore::skrivResultat.name) {
            val keyPrefix = RedisPrefix.Inntekt
            val transaksjonId1 = UUID.randomUUID()
            val transaksjonId2 = UUID.randomUUID()

            val redisStore =
                RedisStore(
                    redis =
                        redisWithMockRedisClient(
                            mockStorageInit =
                                mapOf(
                                    "$keyPrefix#$transaksjonId1#Feilmelding" to "\"dragefrukt\"",
                                    "$keyPrefix#$transaksjonId1#${Key.FNR}" to "\"durian\"",
                                    "$keyPrefix#$transaksjonId2" to "\"rambutan\"",
                                ),
                        ),
                    keyPrefix = keyPrefix,
                )

            redisStore.lesResultat(UUID.randomUUID()).shouldBeNull()
            redisStore.lesResultat(transaksjonId1).shouldBeNull()
            redisStore.lesResultat(transaksjonId2)?.fromJson(String.serializer()) shouldBe "rambutan"

            redisStore.skrivResultat(transaksjonId1, "rabarbra".toJson())

            redisStore.lesResultat(UUID.randomUUID()).shouldBeNull()
            redisStore.lesResultat(transaksjonId1)?.fromJson(String.serializer()) shouldBe "rabarbra"

            redisStore.skrivResultat(transaksjonId1, "en kolossal rabarbra".toJson())

            redisStore.lesResultat(transaksjonId1)?.fromJson(String.serializer()) shouldBe "en kolossal rabarbra"

            // Har ikke blitt overskrevet
            redisStore.lesFeil(transaksjonId1)?.fromJson(String.serializer()) shouldBe "dragefrukt"
            redisStore.lesAlleMellomlagrede(transaksjonId1) shouldContainExactly mapOf(Key.FNR to "durian".toJson())
            redisStore.lesResultat(transaksjonId2)?.fromJson(String.serializer()) shouldBe "rambutan"
        }

        test(RedisStore::skrivFeil.name) {
            val keyPrefix = RedisPrefix.InntektSelvbestemt
            val transaksjonId = UUID.randomUUID()

            val redisStore =
                RedisStore(
                    redis =
                        redisWithMockRedisClient(
                            mockStorageInit =
                                mapOf(
                                    "$keyPrefix#$transaksjonId" to "\"rabarbra\"",
                                    "$keyPrefix#$transaksjonId#${Key.FNR}" to "\"durian\"",
                                ),
                        ),
                    keyPrefix = keyPrefix,
                )

            redisStore.lesFeil(UUID.randomUUID()).shouldBeNull()
            redisStore.lesFeil(transaksjonId).shouldBeNull()

            redisStore.skrivFeil(transaksjonId, "dragefrukt".toJson())

            redisStore.lesFeil(UUID.randomUUID()).shouldBeNull()
            redisStore.lesFeil(transaksjonId)?.fromJson(String.serializer()) shouldBe "dragefrukt"

            redisStore.skrivFeil(transaksjonId, "et lass med dragefrukt".toJson())

            redisStore.lesFeil(transaksjonId)?.fromJson(String.serializer()) shouldBe "et lass med dragefrukt"

            // Har ikke blitt overskrevet
            redisStore.lesResultat(transaksjonId)?.fromJson(String.serializer()) shouldBe "rabarbra"
            redisStore.lesAlleMellomlagrede(transaksjonId) shouldContainExactly mapOf(Key.FNR to "durian".toJson())
        }
    })
