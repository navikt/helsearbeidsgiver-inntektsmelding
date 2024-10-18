package no.nav.helsearbeidsgiver.felles.rapidsrivers.service

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class ServiceRiverStatelessTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockService = spyk(MockService())

        ServiceRiverStateless(mockService).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        context("fail-melding har presedens") {
            withData(
                mapOf(
                    "over data" to
                        mapOf(
                            Key.EVENT_NAME to mockService.eventName.toJson(),
                            Key.UUID to UUID.randomUUID().toJson(),
                            Key.FAIL to Mock.fail.toJson(Fail.serializer()),
                            Key.DATA to mockService.mockSteg1Data().toJson(),
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
            val data =
                mapOf(
                    Key.VIRKSOMHETER to "Fredrikssons Fabrikk".toJson(),
                )

            val innkommendeMelding =
                mapOf(
                    Key.EVENT_NAME to mockService.eventName.toJson(),
                    Key.UUID to UUID.randomUUID().toJson(),
                    Key.DATA to data.toJson(),
                )

            testRapid.sendJson(innkommendeMelding)

            verify {
                mockService.onData(data + innkommendeMelding)
            }
            verify(exactly = 0) {
                mockService.onError(any(), any())
            }
        }

        test("fail-melding håndteres korrekt") {
            val innkommendeMelding =
                mapOf(
                    Key.EVENT_NAME to mockService.eventName.toJson(),
                    Key.UUID to UUID.randomUUID().toJson(),
                    Key.FAIL to Mock.fail.toJson(Fail.serializer()),
                )

            testRapid.sendJson(innkommendeMelding)

            verify {
                mockService.onError(innkommendeMelding, Mock.fail)
            }
            verify(exactly = 0) {
                mockService.onData(any())
            }
        }

        test("ved feil så publiseres ingenting") {
            every { mockService.onData(any()) } throws NullPointerException()

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
                mockService.onData(any())
            }
            verify(exactly = 0) {
                mockService.onError(any(), any())
            }
        }

        context("ignorer melding") {
            context("fail-melding") {
                withData(
                    mapOf(
                        "med uønsket event" to
                            mapOf(
                                Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
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
                                Key.DATA to
                                    mockService
                                        .mockSteg0Data()
                                        .plus(Key.PERSONER to "mock personer".toJson())
                                        .toJson(),
                            ),
                        "med data som flagg" to
                            mapOf(
                                Key.EVENT_NAME to mockService.eventName.toJson(),
                                Key.UUID to UUID.randomUUID().toJson(),
                                Key.BEHOV to BehovType.TILGANGSKONTROLL.toJson(),
                                Key.DATA to "".toJson(),
                                Key.PERSONER to "mock personer".toJson(),
                            ),
                        "med uønsket event" to
                            mapOf(
                                Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
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
