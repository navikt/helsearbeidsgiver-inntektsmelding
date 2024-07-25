package no.nav.helsearbeidsgiver.felles.rapidsrivers.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldNotContainValue
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedis
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class ServiceRiverStatefulTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockRedis = MockRedis(RedisPrefix.HentForespoersel)
        val mockService = spyk(MockService())

        ServiceRiverStateful(mockRedis.store, mockService).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
            mockRedis.setup()
        }

        context("fail-melding har presedens") {
            withData(
                mapOf(
                    "over start" to
                        mapOf(
                            Key.EVENT_NAME to mockService.eventName.toJson(),
                            Key.UUID to UUID.randomUUID().toJson(),
                            Key.FAIL to Mock.fail.toJson(Fail.serializer()),
                            Key.DATA to "".toJson(),
                        ).plus(Mock.values(mockService.startKeys)),
                    "over data" to
                        mapOf(
                            Key.EVENT_NAME to mockService.eventName.toJson(),
                            Key.UUID to UUID.randomUUID().toJson(),
                            Key.FAIL to Mock.fail.toJson(Fail.serializer()),
                            Key.DATA to "".toJson(),
                        ).plus(Mock.values(mockService.dataKeys)),
                    "over start med nested data" to
                        mapOf(
                            Key.EVENT_NAME to mockService.eventName.toJson(),
                            Key.UUID to UUID.randomUUID().toJson(),
                            Key.FAIL to Mock.fail.toJson(Fail.serializer()),
                            Key.DATA to Mock.values(mockService.startKeys).toJson(),
                        ),
                    "over nested data" to
                        mapOf(
                            Key.EVENT_NAME to mockService.eventName.toJson(),
                            Key.UUID to UUID.randomUUID().toJson(),
                            Key.FAIL to Mock.fail.toJson(Fail.serializer()),
                            Key.DATA to Mock.values(mockService.dataKeys).toJson(),
                        ),
                    "over behov (som skal ignoreres)" to
                        mapOf(
                            Key.EVENT_NAME to mockService.eventName.toJson(),
                            Key.UUID to UUID.randomUUID().toJson(),
                            Key.FAIL to Mock.fail.toJson(Fail.serializer()),
                            Key.BEHOV to BehovType.TILGANGSKONTROLL.toJson(),
                        ),
                ),
            ) { innkommendeMelding ->
                // For å passere sjekk for inaktivitet
                every {
                    mockRedis.store.getAll(any())
                } returns Mock.values(mockService.startKeys).mapKeys { it.key.toString() }

                testRapid.sendJson(innkommendeMelding)

                verify {
                    mockService.onError(any(), any())
                }
                verify(exactly = 0) {
                    mockService.onData(any())
                }
            }
        }

        test("datamelding med startdata håndteres korrekt") {
            val transaksjonId = UUID.randomUUID()

            val innkommendeMelding =
                mapOf(
                    Key.EVENT_NAME to mockService.eventName.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.DATA to "".toJson(),
                ).plus(Mock.values(mockService.startKeys))

            testRapid.sendJson(innkommendeMelding)

            val redisStartValues =
                Mock.values(mockService.startKeys).mapKeys {
                    RedisKey.of(transaksjonId, it.key)
                }

            verifyOrder {
                redisStartValues.forEach { (key, value) ->
                    mockRedis.store.set(key, value)
                }
                mockService.onData(innkommendeMelding)
            }
            verify(exactly = 0) {
                mockService.onError(any(), any())
            }
        }

        test("datamelding med nested startdata håndteres korrekt") {
            val transaksjonId = UUID.randomUUID()
            val data = Mock.values(mockService.startKeys)

            val innkommendeMelding =
                mapOf(
                    Key.EVENT_NAME to mockService.eventName.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.DATA to data.toJson(),
                )

            testRapid.sendJson(innkommendeMelding)

            val redisStartValues =
                data.mapKeys {
                    RedisKey.of(transaksjonId, it.key)
                }

            val beriketMelding = data + innkommendeMelding

            verifyOrder {
                redisStartValues.forEach { (key, value) ->
                    mockRedis.store.set(key, value)
                }
                mockService.onData(beriketMelding)
            }
            verify(exactly = 0) {
                mockService.onError(any(), any())
            }
        }

        test("datamelding håndteres korrekt") {
            val transaksjonId = UUID.randomUUID()
            val virksomhetNavn = "Fredrikssons Fabrikk"

            val eksisterendeRedisValues = Mock.values(mockService.startKeys + Key.PERSONER)

            eksisterendeRedisValues.forEach {
                mockRedis.store.set(RedisKey.of(transaksjonId, it.key), it.value)
            }

            val innkommendeMelding =
                mapOf(
                    Key.EVENT_NAME to mockService.eventName.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.DATA to "".toJson(),
                    Key.VIRKSOMHETER to virksomhetNavn.toJson(),
                )

            testRapid.sendJson(innkommendeMelding)

            val allKeys = (mockService.startKeys + mockService.dataKeys).map { RedisKey.of(transaksjonId, it) }.toSet()

            val beriketMelding = eksisterendeRedisValues + innkommendeMelding

            verifyOrder {
                mockRedis.store.set(RedisKey.of(transaksjonId, Key.VIRKSOMHETER), virksomhetNavn.toJson())
                mockRedis.store.getAll(allKeys)
                mockService.onData(beriketMelding)
            }
            verify(exactly = 0) {
                mockService.onError(any(), any())
            }
        }

        test("datamelding med nested data håndteres korrekt") {
            val transaksjonId = UUID.randomUUID()
            val virksomhetNavn = "Fredrikssons Fabrikk"

            val eksisterendeRedisValues = Mock.values(mockService.startKeys + Key.PERSONER)

            eksisterendeRedisValues.forEach {
                mockRedis.store.set(RedisKey.of(transaksjonId, it.key), it.value)
            }

            val data =
                mapOf(
                    Key.VIRKSOMHETER to virksomhetNavn.toJson(),
                )

            val innkommendeMelding =
                mapOf(
                    Key.EVENT_NAME to mockService.eventName.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.DATA to data.toJson(),
                )

            testRapid.sendJson(innkommendeMelding)

            val allKeys = (mockService.startKeys + mockService.dataKeys).map { RedisKey.of(transaksjonId, it) }.toSet()

            val beriketMelding = eksisterendeRedisValues + data + innkommendeMelding

            verifyOrder {
                mockRedis.store.set(RedisKey.of(transaksjonId, Key.VIRKSOMHETER), virksomhetNavn.toJson())
                mockRedis.store.getAll(allKeys)
                mockService.onData(beriketMelding)
            }
            verify(exactly = 0) {
                mockService.onError(any(), any())
            }
        }

        test("fail-melding håndteres korrekt") {
            val eksisterendeRedisValues = Mock.values(mockService.startKeys + Key.VIRKSOMHETER)

            every {
                mockRedis.store.getAll(any())
            } returns eksisterendeRedisValues.mapKeys { it.key.toString() }

            val transaksjonId = UUID.randomUUID()

            val innkommendeMelding =
                mapOf(
                    Key.EVENT_NAME to mockService.eventName.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.FAIL to Mock.fail.toJson(Fail.serializer()),
                )

            testRapid.sendJson(innkommendeMelding)

            val beriketMelding = eksisterendeRedisValues + innkommendeMelding

            val allKeys = (mockService.startKeys + mockService.dataKeys).map { RedisKey.of(transaksjonId, it) }.toSet()

            verifyOrder {
                mockRedis.store.getAll(allKeys)
                mockService.onError(beriketMelding, Mock.fail)
            }
            verify(exactly = 0) {
                mockService.onData(any())
            }
        }

        test("ved feil så publiseres ingenting") {
            every { mockRedis.store.set(any(), any()) } throws NullPointerException()

            val innkommendeMelding =
                mapOf(
                    Key.EVENT_NAME to mockService.eventName.toJson(),
                    Key.UUID to UUID.randomUUID().toJson(),
                    Key.DATA to "".toJson(),
                    Key.VIRKSOMHETER to "Barry Eagles Language Course".toJson(),
                )

            testRapid.sendJson(innkommendeMelding)

            testRapid.inspektør.size shouldBeExactly 0

            verify {
                mockRedis.store.set(any(), any())
            }
            verify(exactly = 0) {
                mockService.onData(any())
                mockService.onError(any(), any())
            }
        }

        test("ved feil så publiseres ingenting (nested data)") {
            every { mockRedis.store.set(any(), any()) } throws NullPointerException()

            val innkommendeMelding =
                mapOf(
                    Key.EVENT_NAME to mockService.eventName.toJson(),
                    Key.UUID to UUID.randomUUID().toJson(),
                    Key.DATA to
                        mapOf(
                            Key.VIRKSOMHETER to "Barry Eagles Language Course".toJson(),
                        ).toJson(),
                )

            testRapid.sendJson(innkommendeMelding)

            testRapid.inspektør.size shouldBeExactly 0

            verify {
                mockRedis.store.set(any(), any())
            }
            verify(exactly = 0) {
                mockService.onData(any())
                mockService.onError(any(), any())
            }
        }

        test("redis-data med ugyldig key ignoreres") {
            val validJson = "gyldig json pga. -->".toJson()

            val validRedisValues = Mock.values(mockService.startKeys)
            val invalidRedisValues =
                mapOf(
                    "ugyldig key" to validJson,
                )

            every {
                mockRedis.store.getAll(any())
            } returns validRedisValues.mapKeys { it.key.toString() }.plus(invalidRedisValues)

            val transaksjonId = UUID.randomUUID()

            val innkommendeMelding =
                mapOf(
                    Key.EVENT_NAME to mockService.eventName.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.FAIL to Mock.fail.toJson(Fail.serializer()),
                )

            testRapid.sendJson(innkommendeMelding)

            val beriketMelding = validRedisValues.plus(innkommendeMelding)

            verifyOrder {
                mockService.onError(
                    withArg {
                        it shouldBe beriketMelding
                        it shouldNotContainValue validJson
                    },
                    Mock.fail,
                )
            }
            verify(exactly = 0) {
                mockService.onData(any())
            }
        }

        context("ignorer melding") {
            context("fail-melding") {
                withData(
                    mapOf(
                        "med uønsket event" to
                            mapOf(
                                Key.EVENT_NAME to EventName.MANUELL_SLETT_SAK_REQUESTED.toJson(),
                                Key.UUID to UUID.randomUUID().toJson(),
                                Key.FAIL to Mock.fail.toJson(Fail.serializer()),
                            ),
                        "med ugyldig fail" to
                            mapOf(
                                Key.EVENT_NAME to mockService.eventName.toJson(),
                                Key.UUID to UUID.randomUUID().toJson(),
                                Key.FAIL to "ugyldig fail".toJson(),
                            ),
                    ),
                ) { innkommendeMelding ->
                    testRapid.sendJson(innkommendeMelding)

                    verify(exactly = 0) {
                        mockService.onData(any())
                        mockService.onError(any(), any())
                    }
                }
            }

            context("datamelding") {
                withData(
                    mapOf(
                        "med behov" to
                            mapOf(
                                Key.EVENT_NAME to mockService.eventName.toJson(),
                                Key.UUID to UUID.randomUUID().toJson(),
                                Key.BEHOV to BehovType.TILGANGSKONTROLL.toJson(),
                                Key.DATA to "".toJson(),
                                *Mock.values(mockService.startKeys).toList().toTypedArray(),
                                Key.PERSONER to "mock personer".toJson(),
                            ),
                        "med behov (nested data)" to
                            mapOf(
                                Key.EVENT_NAME to mockService.eventName.toJson(),
                                Key.UUID to UUID.randomUUID().toJson(),
                                Key.BEHOV to BehovType.TILGANGSKONTROLL.toJson(),
                                Key.DATA to
                                    Mock
                                        .values(mockService.startKeys)
                                        .plus(Key.PERSONER to "mock personer".toJson())
                                        .toJson(),
                            ),
                        "uten alle startdataverdier" to
                            mapOf(
                                Key.EVENT_NAME to mockService.eventName.toJson(),
                                Key.UUID to UUID.randomUUID().toJson(),
                                Key.DATA to "".toJson(),
                                Key.FNR_LISTE to "mock fnr_liste".toJson(),
                            ),
                        "uten alle startdataverdier (nested data)" to
                            mapOf(
                                Key.EVENT_NAME to mockService.eventName.toJson(),
                                Key.UUID to UUID.randomUUID().toJson(),
                                Key.DATA to
                                    mapOf(
                                        Key.FNR_LISTE to "mock fnr_liste".toJson(),
                                    ).toJson(),
                            ),
                        "uten noen dataverdier" to
                            mapOf(
                                Key.EVENT_NAME to mockService.eventName.toJson(),
                                Key.UUID to UUID.randomUUID().toJson(),
                                Key.DATA to "".toJson(),
                            ),
                        "uten noen dataverdier (nested data)" to
                            mapOf(
                                Key.EVENT_NAME to mockService.eventName.toJson(),
                                Key.UUID to UUID.randomUUID().toJson(),
                                Key.DATA to emptyMap<Key, JsonElement>().toJson(),
                            ),
                        "med uønsket event" to
                            mapOf(
                                Key.EVENT_NAME to EventName.MANUELL_SLETT_SAK_REQUESTED.toJson(),
                                Key.UUID to UUID.randomUUID().toJson(),
                                Key.DATA to "".toJson(),
                                Key.PERSONER to "mock personer".toJson(),
                            ),
                        "med uønsket event (nested data)" to
                            mapOf(
                                Key.EVENT_NAME to EventName.MANUELL_SLETT_SAK_REQUESTED.toJson(),
                                Key.UUID to UUID.randomUUID().toJson(),
                                Key.DATA to
                                    mapOf(
                                        Key.PERSONER to "mock personer".toJson(),
                                    ).toJson(),
                            ),
                    ),
                ) { innkommendeMelding ->

                    testRapid.sendJson(innkommendeMelding)

                    verify(exactly = 0) {
                        mockService.onData(any())
                        mockService.onError(any(), any())
                    }
                }
            }

            test("melding som er hverken data- eller fail-melding") {
                testRapid.sendJson(
                    Key.EVENT_NAME to mockService.eventName.toJson(),
                    Key.UUID to UUID.randomUUID().toJson(),
                )

                verify(exactly = 0) {
                    mockService.onData(any())
                    mockService.onError(any(), any())
                }
            }
        }
    })
