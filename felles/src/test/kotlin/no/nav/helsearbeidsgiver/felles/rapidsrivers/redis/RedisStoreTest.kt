package no.nav.helsearbeidsgiver.felles.rapidsrivers.redis

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.test.mock.redisWithMockRedisClient
import no.nav.helsearbeidsgiver.felles.test.shouldContainAllExcludingTempKey
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
                                        "$keyPrefix#$transaksjonId#${Key.SAK_ID}#feil" to "\"papaya\"",
                                        "$keyPrefix#$transaksjonId#${Key.FNR}" to "\"ananas\"",
                                        "$keyPrefix#$transaksjonId#${Key.ORGNRUNDERENHET}" to "\"kokosnøtt\"",
                                        "$keyPrefix#$transaksjonId#${Key.TILGANG}" to null,
                                    ),
                            ),
                        keyPrefix = keyPrefix,
                    )

                redisStore.lesAlleMellomlagrede(UUID.randomUUID()).shouldBeEmpty()

                redisStore.lesAlleMellomlagrede(transaksjonId) shouldContainAllExcludingTempKey
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

                redisStore.lesAlleMellomlagrede(transaksjonId) shouldContainAllExcludingTempKey
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

                redisStore.lesAlleMellomlagrede(transaksjonId) shouldContainAllExcludingTempKey
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
                                    "$keyPrefix#$transaksjonId#${Key.FNR}" to "\"ananas\"",
                                    "$keyPrefix#$transaksjonId#${Key.SAK_ID}#feil" to "\"papaya\"",
                                ),
                        ),
                    keyPrefix = keyPrefix,
                )

            redisStore.lesResultat(UUID.randomUUID()).shouldBeNull()

            redisStore.lesResultat(transaksjonId)?.fromJson(String.serializer()) to "mango"
        }

        test(RedisStore::lesAlleFeil.name) {
            val keyPrefix = RedisPrefix.LagreSelvbestemtIm
            val transaksjonId = UUID.randomUUID()

            val redisStore =
                RedisStore(
                    redis =
                        redisWithMockRedisClient(
                            mockStorageInit =
                                mapOf(
                                    "$transaksjonId" to "\"banan\"",
                                    "$transaksjonId#${Key.SAK_ID}#feil" to "\"appelsin\"",
                                    "$keyPrefix#$transaksjonId" to "\"mango\"",
                                    "$keyPrefix#$transaksjonId#${Key.FNR}" to "\"ananas\"",
                                    "$keyPrefix#$transaksjonId#${Key.SAK_ID}#feil" to "\"papaya\"",
                                    "$keyPrefix#$transaksjonId#${Key.OPPGAVE_ID}#feil" to "\"gojibær\"",
                                ),
                        ),
                    keyPrefix = keyPrefix,
                )

            redisStore.lesAlleFeil(UUID.randomUUID()).shouldBeEmpty()

            redisStore.lesAlleFeil(transaksjonId) shouldContainExactly
                mapOf(
                    Key.SAK_ID to "papaya",
                    Key.OPPGAVE_ID to "gojibær",
                )
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
                                    "$keyPrefix#$transaksjonId#${Key.SAK_ID}#feil" to "\"dragefrukt\"",
                                ),
                        ),
                    keyPrefix = keyPrefix,
                )

            redisStore.lesAlleMellomlagrede(transaksjonId).shouldBeEmpty()

            redisStore.skrivMellomlagring(transaksjonId, Key.FNR, "durian".toJson())

            redisStore.lesAlleMellomlagrede(transaksjonId) shouldContainAllExcludingTempKey
                mapOf(
                    Key.FNR to "durian".toJson(),
                )

            redisStore.skrivMellomlagring(transaksjonId, Key.INNTEKT, "kiwi".toJson())
            redisStore.skrivMellomlagring(transaksjonId, Key.JOURNALPOST_ID, "litchi".toJson())

            redisStore.lesAlleMellomlagrede(transaksjonId) shouldContainAllExcludingTempKey
                mapOf(
                    Key.FNR to "durian".toJson(),
                    Key.INNTEKT to "kiwi".toJson(),
                    Key.JOURNALPOST_ID to "litchi".toJson(),
                )

            redisStore.skrivMellomlagring(transaksjonId, Key.INNTEKT, "granateple".toJson())

            redisStore.lesAlleMellomlagrede(transaksjonId) shouldContainAllExcludingTempKey
                mapOf(
                    Key.FNR to "durian".toJson(),
                    Key.INNTEKT to "granateple".toJson(),
                    Key.JOURNALPOST_ID to "litchi".toJson(),
                )

            redisStore.lesAlleMellomlagrede(UUID.randomUUID()).shouldBeEmpty()

            // Har ikke blitt overskrevet
            redisStore.lesResultat(transaksjonId)?.fromJson(String.serializer()) shouldBe "rabarbra"
            redisStore.lesAlleFeil(transaksjonId) shouldContainExactly mapOf(Key.SAK_ID to "dragefrukt")
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
                                    "$keyPrefix#$transaksjonId1#${Key.FNR}" to "\"durian\"",
                                    "$keyPrefix#$transaksjonId1#${Key.SAK_ID}#feil" to "\"dragefrukt\"",
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
            redisStore.lesAlleFeil(transaksjonId1) shouldContainExactly mapOf(Key.SAK_ID to "dragefrukt")
            redisStore.lesAlleMellomlagrede(transaksjonId1) shouldContainAllExcludingTempKey mapOf(Key.FNR to "durian".toJson())
            redisStore.lesResultat(transaksjonId2)?.fromJson(String.serializer()) shouldBe "rambutan"
        }

        // TODO test mer
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

            redisStore.lesAlleFeil(transaksjonId).shouldBeEmpty()

            redisStore.skrivFeil(transaksjonId, Key.SAK_ID, "dragefrukt")

            redisStore.lesAlleFeil(transaksjonId) shouldContainExactly mapOf(Key.SAK_ID to "dragefrukt")

            redisStore.skrivFeil(transaksjonId, Key.OPPGAVE_ID, "gojibær")
            redisStore.skrivFeil(transaksjonId, Key.SPINN_INNTEKTSMELDING_ID, "jackfrukt")

            redisStore.lesAlleFeil(transaksjonId) shouldContainExactly
                mapOf(
                    Key.SAK_ID to "dragefrukt",
                    Key.OPPGAVE_ID to "gojibær",
                    Key.SPINN_INNTEKTSMELDING_ID to "jackfrukt",
                )

            redisStore.skrivFeil(transaksjonId, Key.SAK_ID, "et lass med dragefrukt")

            redisStore.lesAlleFeil(transaksjonId) shouldContainExactly
                mapOf(
                    Key.SAK_ID to "et lass med dragefrukt",
                    Key.OPPGAVE_ID to "gojibær",
                    Key.SPINN_INNTEKTSMELDING_ID to "jackfrukt",
                )

            redisStore.lesAlleFeil(UUID.randomUUID()).shouldBeEmpty()

            // Har ikke blitt overskrevet
            redisStore.lesResultat(transaksjonId)?.fromJson(String.serializer()) shouldBe "rabarbra"
            redisStore.lesAlleMellomlagrede(transaksjonId) shouldContainAllExcludingTempKey mapOf(Key.FNR to "durian".toJson())
        }
    })
