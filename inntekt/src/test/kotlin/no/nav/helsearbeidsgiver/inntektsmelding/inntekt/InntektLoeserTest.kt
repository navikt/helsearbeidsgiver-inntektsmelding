package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.InntektPerMaaned
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.json.readFail
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.felles.utils.toYearMonth
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.YearMonth
import java.util.UUID

class InntektLoeserTest :
    FunSpec({

        val testRapid = TestRapid()
        val inntektKlient = mockk<InntektKlient>()

        InntektLoeser(testRapid, inntektKlient)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        test("Gir inntekt når klienten svarer med inntekt for orgnr") {
            coEvery {
                inntektKlient.hentInntektPerOrgnrOgMaaned(any(), any(), any(), any(), any())
            } returns
                mapOf(
                    MockInntekt.orgnr.verdi to
                        mapOf(
                            januar(2018) to 10.0,
                            februar(2018) to 11.0,
                            mars(2018) to 12.0,
                        ),
                    "annet orgnr" to
                        mapOf(
                            januar(2018) to 20.0,
                            februar(2018) to 21.0,
                            mars(2018) to 22.0,
                        ),
                )

            testRapid.sendJson(mockInnkommendeMelding())

            val publisert = testRapid.firstMessage().toMap()

            publisert shouldContainKey Key.DATA
            publisert[Key.INNTEKT]?.fromJson(Inntekt.serializer()) shouldBe
                Inntekt(
                    maanedOversikt =
                        listOf(
                            InntektPerMaaned(
                                maaned = januar(2018),
                                inntekt = 10.0,
                            ),
                            InntektPerMaaned(
                                maaned = februar(2018),
                                inntekt = 11.0,
                            ),
                            InntektPerMaaned(
                                maaned = mars(2018),
                                inntekt = 12.0,
                            ),
                        ),
                )
        }

        test("Gir måneder uten inntekt når klienten svarer med inntekt utelukkende for andre orgnr") {
            coEvery {
                inntektKlient.hentInntektPerOrgnrOgMaaned(any(), any(), any(), any(), any())
            } returns
                mapOf(
                    "annet orgnr" to
                        mapOf(
                            januar(2018) to 10.0,
                            februar(2018) to 11.0,
                            mars(2018) to 12.0,
                        ),
                )

            testRapid.sendJson(mockInnkommendeMelding())

            val publisert = testRapid.firstMessage().toMap()

            publisert shouldContainKey Key.DATA
            publisert[Key.INNTEKT]?.fromJson(Inntekt.serializer()) shouldBe
                Inntekt(
                    maanedOversikt =
                        listOf(
                            InntektPerMaaned(
                                maaned = januar(2018),
                                inntekt = null,
                            ),
                            InntektPerMaaned(
                                maaned = februar(2018),
                                inntekt = null,
                            ),
                            InntektPerMaaned(
                                maaned = mars(2018),
                                inntekt = null,
                            ),
                        ),
                )
        }

        test("Setter inn manglende måned med null") {
            coEvery {
                inntektKlient.hentInntektPerOrgnrOgMaaned(any(), any(), any(), any(), any())
            } returns
                mapOf(
                    MockInntekt.orgnr.verdi to
                        mapOf(
                            januar(2018) to 10.0,
                            mars(2018) to 12.0,
                        ),
                    "annet orgnr" to
                        mapOf(
                            januar(2018) to 20.0,
                            februar(2018) to 21.0,
                            mars(2018) to 22.0,
                        ),
                )

            testRapid.sendJson(mockInnkommendeMelding())

            val publisert = testRapid.firstMessage().toMap()

            publisert shouldContainKey Key.DATA
            publisert[Key.INNTEKT]?.fromJson(Inntekt.serializer()) shouldBe
                Inntekt(
                    maanedOversikt =
                        listOf(
                            InntektPerMaaned(
                                maaned = januar(2018),
                                inntekt = 10.0,
                            ),
                            InntektPerMaaned(
                                maaned = februar(2018),
                                inntekt = null,
                            ),
                            InntektPerMaaned(
                                maaned = mars(2018),
                                inntekt = 12.0,
                            ),
                        ),
                )
        }

        test("Svarer med påkrevde felt") {
            coEvery {
                inntektKlient.hentInntektPerOrgnrOgMaaned(any(), any(), any(), any(), any())
            } returns emptyMap()

            testRapid.sendJson(mockInnkommendeMelding())

            val publisert = testRapid.firstMessage().toMap()

            publisert shouldNotContainKey Key.FAIL

            publisert shouldContainKey Key.EVENT_NAME
            publisert shouldContainKey Key.DATA
            publisert shouldContainKey Key.UUID
            publisert shouldContainKey Key.INNTEKT
        }

        test("Kall mot klient bruker korrekte verdier lest fra innkommende melding") {
            coEvery {
                inntektKlient.hentInntektPerOrgnrOgMaaned(any(), any(), any(), any(), any())
            } returns emptyMap()

            testRapid.sendJson(mockInnkommendeMelding())

            coVerifySequence {
                inntektKlient.hentInntektPerOrgnrOgMaaned(
                    fnr = MockInntekt.fnr.verdi,
                    fom = MockInntekt.skjaeringstidspunkt.toYearMonth().minusMonths(3),
                    tom = MockInntekt.skjaeringstidspunkt.toYearMonth().minusMonths(1),
                    navConsumerId = any(),
                    callId = any(),
                )
            }
        }

        test("Feil fra klienten gir feilmelding på rapid") {
            coEvery {
                inntektKlient.hentInntektPerOrgnrOgMaaned(any(), any(), any(), any(), any())
            } throws RuntimeException()

            testRapid.sendJson(mockInnkommendeMelding())

            coVerifySequence {
                inntektKlient.hentInntektPerOrgnrOgMaaned(any(), any(), any(), any(), any())
            }

            val publisert = testRapid.firstMessage().readFail()

            publisert.feilmelding shouldBe "Klarte ikke hente inntekt."
        }

        test("Feil i innkommende melding gir feilmelding på rapid") {
            mockInnkommendeMelding()
                .plus(Key.FNR to "ikke et fnr".toJson())
                .let(testRapid::sendJson)

            coVerify(exactly = 0) {
                inntektKlient.hentInntektPerOrgnrOgMaaned(any(), any(), any(), any(), any())
            }

            val publisert = testRapid.firstMessage().readFail()

            publisert.feilmelding shouldBe "Ukjent feil."
        }

        test("Ukjent feil gir feilmelding på rapid") {
            val mockInntektPerOgnrOgMaaned = mockk<Map<String, Map<YearMonth, Double>>>()

            coEvery {
                inntektKlient.hentInntektPerOrgnrOgMaaned(any(), any(), any(), any(), any())
            } returns mockInntektPerOgnrOgMaaned

            every { mockInntektPerOgnrOgMaaned[any()] } throws RuntimeException()

            testRapid.sendJson(mockInnkommendeMelding())

            coVerifySequence {
                inntektKlient.hentInntektPerOrgnrOgMaaned(any(), any(), any(), any(), any())
            }

            val publisert = testRapid.firstMessage().readFail()

            publisert.feilmelding shouldBe "Ukjent feil."
        }
    })

private object MockInntekt {
    val uuid: UUID = UUID.randomUUID()
    val orgnr = Orgnr.genererGyldig()
    val fnr = Fnr.genererGyldig()
    val skjaeringstidspunkt = 14.april
}

private fun mockInnkommendeMelding(): Map<Key, JsonElement> =
    mapOf(
        Key.EVENT_NAME to EventName.INNTEKT_REQUESTED.toJson(),
        Key.BEHOV to BehovType.HENT_INNTEKT.toJson(),
        Key.UUID to MockInntekt.uuid.toJson(),
        Key.ORGNRUNDERENHET to MockInntekt.orgnr.verdi.toJson(),
        Key.FNR to MockInntekt.fnr.verdi.toJson(),
        Key.SKJAERINGSTIDSPUNKT to MockInntekt.skjaeringstidspunkt.toJson(),
    )
