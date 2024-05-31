package no.nav.helsearbeidsgiver.felles.rapidsrivers.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.maps.shouldNotContainValue
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStoreClassSpecific
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedisAppSpecific
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import java.util.UUID

class ServiceRiverTest : FunSpec({

    val testRapid = TestRapid()
    val mockRedis = MockRedisAppSpecific(RedisPrefix.HentForespoerselService)
    val mockService = spyk(
        MockService(mockRedis.store)
    )

    ServiceRiver(mockService).connect(testRapid)

    beforeTest {
        testRapid.reset()
        clearAllMocks()
        mockRedis.setup()

        // For å passere sjekk for inaktivitet
        every {
            mockRedis.store.getAll(any())
        } returns Mock.values(mockService.startKeys).mapKeys { it.key.toString() }
    }

    context("fail-melding har presedens") {
        withData(
            mapOf(
                "over data" to mapOf(
                    Key.EVENT_NAME to mockService.eventName.toJson(),
                    Key.UUID to UUID.randomUUID().toJson(),
                    Key.FAIL to Mock.fail.toJson(Fail.serializer()),
                    Key.DATA to "".toJson()
                )
                    .plus(Mock.values(mockService.dataKeys)),

                "over start" to mapOf(
                    Key.EVENT_NAME to mockService.eventName.toJson(),
                    Key.UUID to UUID.randomUUID().toJson(),
                    Key.FAIL to Mock.fail.toJson(Fail.serializer())
                )
                    .plus(Mock.values(mockService.startKeys))
            )
        ) { innkommendeMelding ->
            testRapid.sendJson(innkommendeMelding)

            verify {
                mockService.onError(any(), any())
            }
            verify(exactly = 0) {
                mockService.onStart(any())
                mockService.onData(any())
            }
        }
    }

    test("startmelding håndteres korrekt") {
        val clientId = UUID.randomUUID()
        val transaksjonId = UUID.randomUUID()

        val innkommendeMelding = mapOf(
            Key.EVENT_NAME to mockService.eventName.toJson(),
            Key.CLIENT_ID to clientId.toJson()
        )
            .plus(Mock.values(mockService.startKeys))

        mockStatic(::randomUuid) {
            every { randomUuid() } returns transaksjonId

            testRapid.sendJson(innkommendeMelding)
        }

        val clientIdRedisKey = RedisKey.of(transaksjonId, mockService.eventName)

        val redisStartValues = Mock.values(mockService.startKeys).mapKeys {
            RedisKey.of(transaksjonId, it.key)
        }

        verifyOrder {
            redisStartValues.forEach { (key, value) ->
                mockRedis.store.set(key, value)
            }
            mockRedis.store.get(clientIdRedisKey)
            mockRedis.store.set(clientIdRedisKey, clientId.toJson())
            mockService.onStart(
                innkommendeMelding.plus(Key.UUID to transaksjonId.toJson())
            )
        }
        verify(exactly = 0) {
            mockService.onData(any())
            mockService.onError(any(), any())
        }
    }

    test("startmelding overskriver transaksjon-ID") {
        val innkommendeTransaksjonId = UUID.randomUUID()
        val generertTransaksjonId = UUID.randomUUID()

        mockStatic(::randomUuid) {
            every { randomUuid() } returns generertTransaksjonId

            testRapid.sendJson(
                Key.EVENT_NAME to mockService.eventName.toJson(),
                Key.CLIENT_ID to UUID.randomUUID().toJson(),
                Key.UUID to innkommendeTransaksjonId.toJson(),
                *Mock.values(mockService.startKeys).toList().toTypedArray()
            )
        }

        val clientIdRedisKey = RedisKey.of(generertTransaksjonId, mockService.eventName)

        val redisStartValues = Mock.values(mockService.startKeys).mapKeys {
            RedisKey.of(generertTransaksjonId, it.key)
        }

        verifyOrder {
            redisStartValues.forEach { (key, value) ->
                mockRedis.store.set(key, value)
            }
            mockRedis.store.get(clientIdRedisKey)
            mockRedis.store.set(clientIdRedisKey, any())
            mockService.onStart(
                withArg {
                    it[Key.UUID] shouldBe generertTransaksjonId.toJson()
                    it shouldNotContainValue innkommendeTransaksjonId.toJson()
                }
            )
        }
        verify(exactly = 0) {
            mockService.onData(any())
            mockService.onError(any(), any())
        }
    }

    test("startmelding trigger ikke service ved eksisterende client-ID") {
        val eksisterendeClientId = UUID.randomUUID()

        every { mockRedis.store.get(any()) } returns eksisterendeClientId.toJson()

        val transaksjonId = UUID.randomUUID()

        mockStatic(::randomUuid) {
            every { randomUuid() } returns transaksjonId

            testRapid.sendJson(
                Key.EVENT_NAME to mockService.eventName.toJson(),
                Key.CLIENT_ID to UUID.randomUUID().toJson(),
                *Mock.values(mockService.startKeys).toList().toTypedArray()
            )
        }

        val clientIdRedisKey = RedisKey.of(transaksjonId, mockService.eventName)

        val redisStartValues = Mock.values(mockService.startKeys).mapKeys {
            RedisKey.of(transaksjonId, it.key)
        }

        verifyOrder {
            redisStartValues.forEach { (key, value) ->
                mockRedis.store.set(key, value)
            }
            mockRedis.store.get(clientIdRedisKey)
        }
        verify(exactly = 0) {
            mockRedis.store.set(clientIdRedisKey, any())
            mockService.onStart(any())
            mockService.onData(any())
            mockService.onError(any(), any())
        }
    }

    test("datamelding håndteres korrekt") {
        val eksisterendeRedisValues = Mock.values(mockService.startKeys + Key.PERSONER)

        every {
            mockRedis.store.getAll(any())
        } returns eksisterendeRedisValues.mapKeys { it.key.toString() }

        val transaksjonId = UUID.randomUUID()
        val virksomhetNavn = "Fredrikssons Fabrikk"

        val innkommendeMelding = mapOf(
            Key.EVENT_NAME to mockService.eventName.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to "".toJson(),
            Key.VIRKSOMHETER to virksomhetNavn.toJson()
        )

        testRapid.sendJson(innkommendeMelding)

        val beriketMelding = eksisterendeRedisValues + innkommendeMelding

        val allKeys = (mockService.startKeys + mockService.dataKeys).map { RedisKey.of(transaksjonId, it) }.toSet()

        verifyOrder {
            mockRedis.store.set(RedisKey.of(transaksjonId, Key.VIRKSOMHETER), virksomhetNavn.toJson())
            mockRedis.store.getAll(allKeys)
            mockService.onData(beriketMelding)
        }
        verify(exactly = 0) {
            mockService.onStart(any())
            mockService.onError(any(), any())
        }
    }

    test("fail-melding håndteres korrekt") {
        val eksisterendeRedisValues = Mock.values(mockService.startKeys + Key.VIRKSOMHETER)

        every {
            mockRedis.store.getAll(any())
        } returns eksisterendeRedisValues.mapKeys { it.key.toString() }

        val transaksjonId = UUID.randomUUID()

        val innkommendeMelding = mapOf(
            Key.EVENT_NAME to mockService.eventName.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FAIL to Mock.fail.toJson(Fail.serializer())
        )

        testRapid.sendJson(innkommendeMelding)

        val beriketMelding = eksisterendeRedisValues + innkommendeMelding

        val allKeys = (mockService.startKeys + mockService.dataKeys).map { RedisKey.of(transaksjonId, it) }.toSet()

        verifyOrder {
            mockRedis.store.getAll(allKeys)
            mockService.onError(beriketMelding, Mock.fail)
        }
        verify(exactly = 0) {
            mockService.onStart(any())
            mockService.onData(any())
        }
    }

    test("ved feil så publiseres ingenting") {
        every { mockRedis.store.set(any(), any()) } throws NullPointerException()

        val innkommendeMelding = mapOf(
            Key.EVENT_NAME to mockService.eventName.toJson(),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.DATA to "".toJson(),
            Key.VIRKSOMHETER to "Barry Eagles Language Course".toJson()
        )

        testRapid.sendJson(innkommendeMelding)

        testRapid.inspektør.size shouldBeExactly 0

        verify {
            mockRedis.store.set(any(), any())
        }
        verify(exactly = 0) {
            mockService.onStart(any())
            mockService.onData(any())
            mockService.onError(any(), any())
        }
    }

    test("redis-data med feil ignoreres") {
        val validJson = "gyldig json pga. -->".toJson()

        val validRedisValues = Mock.values(mockService.startKeys)
        val invalidRedisValues = mapOf(
            "ugyldig key" to validJson
        )

        every {
            mockRedis.store.getAll(any())
        } returns validRedisValues.mapKeys { it.key.toString() }.plus(invalidRedisValues)

        val transaksjonId = UUID.randomUUID()

        val innkommendeMelding = mapOf(
            Key.EVENT_NAME to mockService.eventName.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FAIL to Mock.fail.toJson(Fail.serializer())
        )

        testRapid.sendJson(innkommendeMelding)

        val beriketMelding = validRedisValues.plus(innkommendeMelding)

        verifyOrder {
            mockService.onError(
                withArg {
                    it shouldBe beriketMelding
                    it shouldNotContainKey Key.PERSONER
                    it shouldNotContainValue validJson
                },
                Mock.fail
            )
        }
        verify(exactly = 0) {
            mockService.onStart(any())
            mockService.onData(any())
        }
    }

    test("datamelding trigger ikke service ved manglende startdata (inaktiv service)") {
        every {
            mockRedis.store.getAll(any())
        } returns Mock.values(setOf(Key.PERSONER)).mapKeys { it.key.toString() }

        val transaksjonId = UUID.randomUUID()
        val virksomhetNavn = "Terkels Sabeltannisbutikk"

        val innkommendeMelding = mapOf(
            Key.EVENT_NAME to mockService.eventName.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to "".toJson(),
            Key.VIRKSOMHETER to virksomhetNavn.toJson()
        )

        testRapid.sendJson(innkommendeMelding)

        verifyOrder {
            mockRedis.store.set(RedisKey.of(transaksjonId, Key.VIRKSOMHETER), virksomhetNavn.toJson())
            mockRedis.store.getAll(any())
        }
        verify(exactly = 0) {
            mockService.onStart(any())
            mockService.onData(any())
            mockService.onError(any(), any())
        }
    }

    test("fail-melding trigger ikke service ved manglende startdata (inaktiv service)") {
        every {
            mockRedis.store.getAll(any())
        } returns Mock.values(setOf(Key.VIRKSOMHETER)).mapKeys { it.key.toString() }

        val transaksjonId = UUID.randomUUID()

        val innkommendeMelding = mapOf(
            Key.EVENT_NAME to mockService.eventName.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FAIL to Mock.fail.toJson(Fail.serializer())
        )

        testRapid.sendJson(innkommendeMelding)

        verifyOrder {
            mockRedis.store.getAll(any())
        }
        verify(exactly = 0) {
            mockService.onStart(any())
            mockService.onData(any())
            mockService.onError(any(), any())
        }
    }

    context("ignorer melding") {
        context("fail-melding") {
            withData(
                mapOf(
                    "med uønsket event" to mapOf(
                        Key.EVENT_NAME to EventName.MANUELL_SLETT_SAK_REQUESTED.toJson(),
                        Key.UUID to UUID.randomUUID().toJson(),
                        Key.FAIL to Mock.fail.toJson(Fail.serializer())
                    ),

                    "med ugyldig fail" to mapOf(
                        Key.EVENT_NAME to mockService.eventName.toJson(),
                        Key.UUID to UUID.randomUUID().toJson(),
                        Key.FAIL to "ugyldig fail".toJson(String.serializer())
                    )
                )
            ) { innkommendeMelding ->
                testRapid.sendJson(innkommendeMelding)

                verify(exactly = 0) {
                    mockService.onStart(any())
                    mockService.onData(any())
                    mockService.onError(any(), any())
                }
            }
        }

        context("datamelding") {
            withData(
                mapOf(
                    "uten dataverdier" to mapOf(
                        Key.EVENT_NAME to mockService.eventName.toJson(),
                        Key.UUID to UUID.randomUUID().toJson(),
                        Key.DATA to "".toJson()
                    ),

                    "med uønsket event" to mapOf(
                        Key.EVENT_NAME to EventName.MANUELL_SLETT_SAK_REQUESTED.toJson(),
                        Key.UUID to UUID.randomUUID().toJson(),
                        Key.DATA to "".toJson(),
                        Key.PERSONER to "mock personer".toJson()
                    )
                )
            ) { innkommendeMelding ->

                testRapid.sendJson(innkommendeMelding)

                verify(exactly = 0) {
                    mockService.onStart(any())
                    mockService.onData(any())
                    mockService.onError(any(), any())
                }
            }
        }

        context("startmelding") {
            withData(
                mapOf(
                    "med behov" to mapOf(
                        Key.EVENT_NAME to mockService.eventName.toJson(),
                        Key.UUID to UUID.randomUUID().toJson(),
                        Key.BEHOV to "mock behov".toJson()
                    )
                        .plus(Mock.values(mockService.startKeys)),

                    "med data" to mapOf(
                        Key.EVENT_NAME to mockService.eventName.toJson(),
                        Key.UUID to UUID.randomUUID().toJson(),
                        Key.DATA to "mock data".toJson()
                    )
                        .plus(Mock.values(mockService.startKeys)),

                    "uten alle startdataverdier" to mapOf(
                        Key.EVENT_NAME to mockService.eventName.toJson(),
                        Key.UUID to UUID.randomUUID().toJson(),
                        Key.FNR_LISTE to "mock fnr_liste".toJson()
                    ),

                    "med uønsket event" to mapOf(
                        Key.EVENT_NAME to EventName.MANUELL_SLETT_SAK_REQUESTED.toJson(),
                        Key.UUID to UUID.randomUUID().toJson()
                    )
                        .plus(Mock.values(mockService.startKeys))
                )
            ) { innkommendeMelding ->
                testRapid.sendJson(innkommendeMelding)

                verify(exactly = 0) {
                    mockService.onStart(any())
                    mockService.onData(any())
                    mockService.onError(any(), any())
                }
            }
        }

        test("melding som er hverken start-, data- eller fail-melding") {
            testRapid.sendJson(
                Key.EVENT_NAME to mockService.eventName.toJson(),
                Key.UUID to UUID.randomUUID().toJson()
            )

            verify(exactly = 0) {
                mockService.onStart(any())
                mockService.onData(any())
                mockService.onError(any(), any())
            }
        }
    }
})

private class MockService(
    override val redisStore: RedisStoreClassSpecific
) : Service() {
    override val eventName = EventName.MANUELL_OPPRETT_SAK_REQUESTED
    override val startKeys = setOf(
        Key.FNR_LISTE,
        Key.ER_DUPLIKAT_IM
    )
    override val dataKeys = setOf(
        Key.PERSONER,
        Key.VIRKSOMHETER
    )

    override fun onStart(melding: Map<Key, JsonElement>) {}
    override fun onData(melding: Map<Key, JsonElement>) {}
    override fun onError(melding: Map<Key, JsonElement>, fail: Fail) {}
}

private object Mock {
    val fail = Fail(
        feilmelding = "Noen har blandet ut flybensinen med Red Bull.",
        event = EventName.INNTEKTSMELDING_MOTTATT,
        transaksjonId = UUID.randomUUID(),
        forespoerselId = UUID.randomUUID(),
        utloesendeMelding = JsonNull
    )

    fun values(keys: Set<Key>): Map<Key, JsonElement> =
        keys.associateWith { "mock $it".toJson() }
}
