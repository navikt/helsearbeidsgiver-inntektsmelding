package no.nav.hag.simba.utils.rr.service

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.test.json.plusData
import no.nav.hag.simba.utils.felles.test.mock.mockFail
import no.nav.hag.simba.utils.rr.test.mockConnectToRapid
import no.nav.hag.simba.utils.rr.test.sendJson
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.hag.simba.utils.valkey.test.MockRedis
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class ServiceRiverStatefulTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockRedis = MockRedis(RedisPrefix.HentForespoersel)
        val mockService =
            spyk(
                MockServiceMedRedis(mockRedis.store),
            )
        val mockFail = mockFail("Noen har blandet ut flybensinen med Red Bull.", mockService.eventName)

        mockConnectToRapid(testRapid) {
            listOf(
                ServiceRiverStateful(mockService),
            )
        }

        beforeTest {
            testRapid.reset()
            clearAllMocks()
            mockRedis.setup()
        }

        context("fail-melding har presedens") {
            withData(
                mapOf(
                    "over data" to
                        mapOf(
                            Key.EVENT_NAME to mockService.eventName.toJson(),
                            Key.KONTEKST_ID to mockFail.kontekstId.toJson(),
                            Key.FAIL to mockFail.toJson(Fail.serializer()),
                            Key.DATA to mockService.mockSteg1Data().toJson(),
                        ),
                    "over behov (som skal ignoreres)" to
                        mapOf(
                            Key.EVENT_NAME to mockService.eventName.toJson(),
                            Key.KONTEKST_ID to mockFail.kontekstId.toJson(),
                            Key.FAIL to mockFail.toJson(Fail.serializer()),
                            Key.BEHOV to BehovType.TILGANGSKONTROLL.toJson(),
                        ),
                ),
            ) { innkommendeMelding ->
                testRapid.sendJson(innkommendeMelding)

                verify {
                    mockService.onError(any(), any())
                }
                verify(exactly = 0) {
                    mockService.onData(any())
                }
            }
        }

        test("datamelding håndteres korrekt") {
            val kontekstId = UUID.randomUUID()
            val virksomhetNavn = "Fredrikssons Fabrikk"

            val eksisterendeRedisValues =
                mockService
                    .mockSteg0Data()
                    .plus(
                        mapOf(
                            Key.PERSONER to "mock personer".toJson(),
                            Key.FORESPOERSEL_ID to "mock forespoersel_id".toJson(),
                        ),
                    )

            eksisterendeRedisValues.forEach {
                mockRedis.store.skrivMellomlagring(kontekstId, it.key, it.value)
            }

            val innkommendeMeldingUtenData =
                mapOf(
                    Key.EVENT_NAME to mockService.eventName.toJson(),
                    Key.KONTEKST_ID to kontekstId.toJson(),
                )

            val data =
                mapOf(
                    Key.VIRKSOMHETER to virksomhetNavn.toJson(),
                    Key.EVENT_NAME to "blir overskrevet av rotnivå".toJson(),
                    Key.FORESPOERSEL_ID to "overskriver Redis-verdi".toJson(),
                )

            testRapid.sendJson(innkommendeMeldingUtenData.plusData(data))

            // På rotnivå, så forventes det at eksisterende data fra Redis prioriteres under innkommende data, som igjen prioriteres under innkommende felt fra rotnivå
            // På datanivå, så forventes det at eksisterende data fra Redis prioriteres under innkommende data
            val forventetBeriketMelding = (eksisterendeRedisValues + data + innkommendeMeldingUtenData).plusData(eksisterendeRedisValues + data)

            verifyOrder {
                mockRedis.store.skrivMellomlagring(kontekstId, Key.VIRKSOMHETER, virksomhetNavn.toJson())
                mockRedis.store.lesAlleMellomlagrede(kontekstId)
                mockService.onData(forventetBeriketMelding)
            }
            verify(exactly = 0) {
                mockService.onError(any(), any())
            }
        }

        test("fail-melding håndteres korrekt") {
            val eksisterendeRedisValues =
                mockService
                    .mockSteg0Data()
                    .plus(Key.VIRKSOMHETER to "mock virksomheter".toJson())

            every { mockRedis.store.lesAlleMellomlagrede(any()) } returns eksisterendeRedisValues

            val innkommendeMelding =
                mapOf(
                    Key.FAIL to mockFail.toJson(Fail.serializer()),
                    Key.EVENT_NAME to "ignoreres".toJson(),
                    Key.KONTEKST_ID to "ignoreres".toJson(),
                    Key.DATA to
                        mapOf(
                            Key.VIRKSOMHETER to "ignoreres".toJson(),
                        ).toJson(),
                )

            testRapid.sendJson(innkommendeMelding)

            val beriketMelding =
                mapOf(
                    Key.EVENT_NAME to mockService.eventName.toJson(),
                    Key.KONTEKST_ID to mockFail.kontekstId.toJson(),
                    Key.DATA to eksisterendeRedisValues.toJson(),
                ).plus(eksisterendeRedisValues)

            verifyOrder {
                mockRedis.store.lesAlleMellomlagrede(mockFail.kontekstId)
                mockService.onError(beriketMelding, mockFail)
            }
            verify(exactly = 0) {
                mockService.onData(any())
            }
        }

        test("ved feil så publiseres ingenting") {
            every { mockRedis.store.skrivMellomlagring(any(), any(), any()) } throws NullPointerException()

            val innkommendeMelding =
                mapOf(
                    Key.EVENT_NAME to mockService.eventName.toJson(),
                    Key.KONTEKST_ID to UUID.randomUUID().toJson(),
                    Key.DATA to
                        mapOf(
                            Key.VIRKSOMHETER to "Barry Eagles Language Course".toJson(),
                        ).toJson(),
                )

            testRapid.sendJson(innkommendeMelding)

            testRapid.inspektør.size shouldBeExactly 0

            verify {
                mockRedis.store.skrivMellomlagring(any(), any(), any())
            }
            verify(exactly = 0) {
                mockService.onData(any())
                mockService.onError(any(), any())
            }
        }

        context("ignorer melding") {
            context("fail-melding") {
                withData(
                    mapOf(
                        "med uønsket event" to
                            mapOf(
                                Key.FAIL to
                                    mockFail(
                                        "You punched a bursar?",
                                        EventName.KVITTERING_REQUESTED,
                                    ).toJson(Fail.serializer()),
                            ),
                        "med ugyldig fail" to
                            mapOf(
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
                                Key.KONTEKST_ID to UUID.randomUUID().toJson(),
                                Key.BEHOV to BehovType.TILGANGSKONTROLL.toJson(),
                                Key.DATA to
                                    mockService
                                        .mockSteg0Data()
                                        .plus(Key.PERSONER to "mock personer".toJson())
                                        .toJson(),
                            ),
                        "med data som flagg" to
                            mapOf(
                                Key.EVENT_NAME to mockService.eventName.toJson(),
                                Key.KONTEKST_ID to UUID.randomUUID().toJson(),
                                Key.BEHOV to BehovType.TILGANGSKONTROLL.toJson(),
                                Key.DATA to "".toJson(),
                                Key.PERSONER to "mock personer".toJson(),
                            ),
                        "med uønsket event" to
                            mapOf(
                                Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
                                Key.KONTEKST_ID to UUID.randomUUID().toJson(),
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
                    Key.KONTEKST_ID to UUID.randomUUID().toJson(),
                )

                verify(exactly = 0) {
                    mockService.onData(any())
                    mockService.onError(any(), any())
                }
            }
        }
    })
