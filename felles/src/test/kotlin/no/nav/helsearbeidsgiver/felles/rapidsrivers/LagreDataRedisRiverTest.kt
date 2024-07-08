package no.nav.helsearbeidsgiver.felles.rapidsrivers

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.InntektPerMaaned
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedis
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.mars
import java.util.UUID

class LagreDataRedisRiverTest : FunSpec({

    val testRapid = TestRapid()
    val mockRedis = MockRedis()
    val mockListener = mockk<River.PacketListener>(relaxed = true)

    val event = EventName.MANUELL_OPPRETT_SAK_REQUESTED
    val dataKeys = setOf(Key.FNR, Key.INNTEKT)

    LagreDataRedisRiver(
        event,
        dataKeys,
        testRapid,
        mockRedis.store,
        mockListener::onPacket,
    )

    beforeEach {
        testRapid.reset()
        clearAllMocks()
        mockRedis.setup()
    }

    test("Alle nøkler fra 'dataKeys' lagres (med verdi) i Redis når melding inkluderer DATA-nøkkel") {
        val transaksjonId = UUID.randomUUID()

        testRapid.sendJson(
            Key.EVENT_NAME to event.toJson(),
            Key.DATA to "".toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FNR to Mock.FNR.toJson(),
            Key.INNTEKT to Mock.inntekt.toJson(Inntekt.serializer()),
        )

        verifySequence {
            mockRedis.store.set(RedisKey.of(transaksjonId, Key.FNR), Mock.FNR.toJsonStr(String.serializer()))
            mockRedis.store.set(RedisKey.of(transaksjonId, Key.INNTEKT), Mock.inntekt.toJsonStr(Inntekt.serializer()))
        }
    }

    test("Enkelt nøkkel fra 'dataKeys' lagres (med verdi) i Redis når melding inkluderer DATA-nøkkel") {
        val transaksjonId = UUID.randomUUID()

        testRapid.sendJson(
            Key.EVENT_NAME to event.toJson(),
            Key.DATA to "".toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FNR to Mock.FNR.toJson(),
        )

        verifySequence {
            mockRedis.store.set(RedisKey.of(transaksjonId, Key.FNR), Mock.FNR.toJsonStr(String.serializer()))
        }
    }

    test("Nøkler fra 'dataKeys' lagres _ikke_ i Redis når melding _mangler_ DATA-nøkkel") {
        val transaksjonId = UUID.randomUUID()

        testRapid.sendJson(
            Key.EVENT_NAME to event.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FNR to Mock.FNR.toJson(),
        )

        verify(exactly = 0) {
            mockRedis.store.set(RedisKey.of(transaksjonId, Key.FNR), any())
        }
    }

    test("Nøkler fra 'dataKeys' lagres _ikke_ i Redis når melding _mangler_ EVENT_NAME") {
        val transaksjonId = UUID.randomUUID()

        testRapid.sendJson(
            Key.DATA to "".toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FNR to Mock.FNR.toJson(),
        )

        verify(exactly = 0) {
            mockRedis.store.set(RedisKey.of(transaksjonId, Key.FNR), any())
        }
    }

    test("Nøkler fra 'dataKeys' lagres _ikke_ i Redis når melding har feil EVENT_NAME") {
        val transaksjonId = UUID.randomUUID()

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.AKTIVE_ORGNR_REQUESTED.toJson(),
            Key.DATA to "".toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FNR to Mock.FNR.toJson(),
        )

        verify(exactly = 0) {
            mockRedis.store.set(RedisKey.of(transaksjonId, Key.FNR), any())
        }
    }

    test("Nøkler fra 'dataKeys' lagres _ikke_ i Redis når melding inkluderer BEHOV-nøkkel") {
        val transaksjonId = UUID.randomUUID()

        testRapid.sendJson(
            Key.EVENT_NAME to event.toJson(),
            Key.BEHOV to BehovType.INNTEKT.toJson(),
            Key.DATA to "".toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FNR to Mock.FNR.toJson(),
        )

        verify(exactly = 0) {
            mockRedis.store.set(RedisKey.of(transaksjonId, Key.FNR), any())
        }
    }

    test("Nøkler fra 'dataKeys' lagres _ikke_ i Redis når melding _mangler_ UUID-nøkkel") {
        testRapid.sendJson(
            Key.EVENT_NAME to event.toJson(),
            Key.DATA to "".toJson(),
            Key.FNR to Mock.FNR.toJson(),
        )

        verify(exactly = 0) {
            mockRedis.store.set(any(), any())
        }
    }

    test("Nøkler fra 'dataKeys' lagres _ikke_ i Redis når melding har UUID på feil format") {
        val transaksjonIdMedFeilFormat = "ikke en uuid"

        testRapid.sendJson(
            Key.EVENT_NAME to event.toJson(),
            Key.DATA to "".toJson(),
            Key.UUID to transaksjonIdMedFeilFormat.toJson(),
            Key.FNR to Mock.FNR.toJson(),
        )

        verify(exactly = 0) {
            mockRedis.store.set(any(), any())
        }
    }

    test("Nøkler utenfor 'dataKeys' lagres _ikke_ i Redis når melding inkluderer DATA-nøkkel") {
        val transaksjonId = UUID.randomUUID()

        testRapid.sendJson(
            Key.EVENT_NAME to event.toJson(),
            Key.DATA to "".toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FNR to Mock.FNR.toJson(),
            Key.ORGNRUNDERENHET to Mock.ORGNR.toJson(),
        )

        verify {
            mockRedis.store.set(RedisKey.of(transaksjonId, Key.FNR), Mock.FNR.toJsonStr(String.serializer()))
        }
        verify(exactly = 0) {
            mockRedis.store.set(RedisKey.of(transaksjonId, Key.ORGNRUNDERENHET), any())
        }
    }

    test("Nøkler utenfor 'dataKeys' lagres _ikke_ i Redis når melding _mangler_ DATA-nøkkel") {
        val transaksjonId = UUID.randomUUID()

        testRapid.sendJson(
            Key.EVENT_NAME to event.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.ORGNRUNDERENHET to Mock.ORGNR.toJson(),
        )

        verify(exactly = 0) {
            mockRedis.store.set(RedisKey.of(transaksjonId, Key.ORGNRUNDERENHET), any())
        }
    }

    test("Argumentfunksjon 'etterDataLagret' kalles når data har blitt lagret") {
        val transaksjonId = UUID.randomUUID()

        val innkommendeMelding =
            mapOf(
                Key.EVENT_NAME to event.toJson(),
                Key.DATA to "".toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FNR to Mock.FNR.toJson(),
                Key.INNTEKT to Mock.inntekt.toJson(Inntekt.serializer()),
            )

        testRapid.sendJson(
            *innkommendeMelding.toList().toTypedArray(),
        )

        verifySequence {
            mockRedis.store.set(RedisKey.of(transaksjonId, Key.FNR), any())
            mockRedis.store.set(RedisKey.of(transaksjonId, Key.INNTEKT), any())

            mockListener.onPacket(
                withArg {
                    val melding = it.toJson().parseJson().toMap()

                    melding shouldContainExactly innkommendeMelding
                },
                any(),
            )
        }
    }

    test("Argumentfunksjon 'etterDataLagret' kalles _ikke_ når data _ikke_ har blitt lagret") {
        val transaksjonId = UUID.randomUUID()

        testRapid.sendJson(
            Key.EVENT_NAME to event.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FNR to Mock.FNR.toJson(),
            Key.INNTEKT to Mock.inntekt.toJson(Inntekt.serializer()),
        )

        verify(exactly = 0) {
            mockRedis.store.set(any(), any())

            mockListener.onPacket(any(), any())
        }
    }
})

private object Mock {
    const val FNR = "11223355077"
    const val ORGNR = "888222888"

    val inntekt =
        Inntekt(
            maanedOversikt =
                listOf(
                    InntektPerMaaned(
                        maaned = mars(2020),
                        inntekt = 17.18,
                    ),
                    InntektPerMaaned(
                        maaned = april(2020),
                        inntekt = 0.12,
                    ),
                ),
        )
}
