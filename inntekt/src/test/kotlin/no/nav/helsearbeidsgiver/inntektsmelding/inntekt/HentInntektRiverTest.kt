package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.mockk
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Inntekt
import no.nav.helsearbeidsgiver.felles.domene.InntektPerMaaned
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.mockFail
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.inntektsmelding.inntekt.Mock.toMap
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.juli
import no.nav.helsearbeidsgiver.utils.test.date.juni
import no.nav.helsearbeidsgiver.utils.test.date.mai
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.date.september
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.LocalDate
import java.util.UUID

class HentInntektRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockInntektClient = mockk<InntektKlient>()

        HentInntektRiver(mockInntektClient).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        test("henter inntekt") {
            val innkommendeMelding = Mock.innkommendeMelding(inntektsdato = 12.august)

            val inntektPerOrgnrOgMaaned =
                mapOf(
                    innkommendeMelding.orgnr.verdi to
                        mapOf(
                            april(2018) to 14_000.0,
                            mai(2018) to 15_000.0,
                            juni(2018) to 16_000.0,
                            juli(2018) to 17_000.0,
                            august(2018) to 18_000.0,
                            september(2018) to 19_000.0,
                        ),
                    Orgnr.genererGyldig().verdi to
                        mapOf(
                            mars(2018) to 10_003.0,
                            april(2018) to 10_004.0,
                            mai(2018) to 10_005.0,
                            juni(2018) to 10_006.0,
                            juli(2018) to 10_007.0,
                        ),
                )

            val forventetInntekt =
                Inntekt(
                    maanedOversikt =
                        listOf(
                            InntektPerMaaned(
                                maaned = mai(2018),
                                inntekt = 15_000.0,
                            ),
                            InntektPerMaaned(
                                maaned = juni(2018),
                                inntekt = 16_000.0,
                            ),
                            InntektPerMaaned(
                                maaned = juli(2018),
                                inntekt = 17_000.0,
                            ),
                        ),
                )

            coEvery { mockInntektClient.hentInntektPerOrgnrOgMaaned(any(), any(), any(), any(), any()) } returns inntektPerOrgnrOgMaaned

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                    Key.KONTEKST_ID to innkommendeMelding.transaksjonId.toJson(),
                    Key.DATA to
                        innkommendeMelding.data
                            .plus(Key.INNTEKT to forventetInntekt.toJson(Inntekt.serializer()))
                            .toJson(),
                )

            coVerifySequence {
                mockInntektClient.hentInntektPerOrgnrOgMaaned(innkommendeMelding.fnr.verdi, mai(2018), juli(2018), any(), any())
            }
        }

        test("håndterer feil") {
            coEvery { mockInntektClient.hentInntektPerOrgnrOgMaaned(any(), any(), any(), any(), any()) } throws NullPointerException()

            val innkommendeMelding = Mock.innkommendeMelding(18.april)

            val forventetFail =
                Fail(
                    feilmelding = "Klarte ikke hente inntekt fra Inntektskomponenten.",
                    kontekstId = innkommendeMelding.transaksjonId,
                    utloesendeMelding = innkommendeMelding.toMap(),
                )

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()

            coVerifySequence {
                mockInntektClient.hentInntektPerOrgnrOgMaaned(innkommendeMelding.fnr.verdi, januar(2018), mars(2018), any(), any())
            }
        }

        context("ignorerer melding") {
            withData(
                mapOf(
                    "melding med ukjent behov" to Pair(Key.BEHOV, BehovType.HENT_SELVBESTEMT_IM.toJson()),
                    "melding med data som flagg" to Pair(Key.DATA, "".toJson()),
                    "melding med fail" to Pair(Key.FAIL, Mock.fail.toJson(Fail.serializer())),
                ),
            ) { uoensketKeyMedVerdi ->
                testRapid.sendJson(
                    Mock
                        .innkommendeMelding(2.januar)
                        .toMap()
                        .plus(uoensketKeyMedVerdi),
                )

                testRapid.inspektør.size shouldBeExactly 0

                coVerify(exactly = 0) {
                    mockInntektClient.hentInntektPerOrgnrOgMaaned(any(), any(), any(), any(), any())
                }
            }
        }
    })

private object Mock {
    fun innkommendeMelding(inntektsdato: LocalDate): Melding {
        val orgnr = Orgnr.genererGyldig()
        val fnr = Fnr.genererGyldig()

        return Melding(
            eventName = EventName.TRENGER_REQUESTED,
            behovType = BehovType.HENT_INNTEKT,
            transaksjonId = UUID.randomUUID(),
            data =
                mapOf(
                    Key.ORGNRUNDERENHET to orgnr.toJson(),
                    Key.FNR to fnr.toJson(),
                    Key.INNTEKTSDATO to inntektsdato.toJson(),
                ),
            orgnr = orgnr,
            fnr = fnr,
            inntektsdato = inntektsdato,
        )
    }

    fun Melding.toMap(): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to behovType.toJson(),
            Key.KONTEKST_ID to transaksjonId.toJson(),
            Key.DATA to data.toJson(),
        )

    val fail = mockFail("Elementary, my dear Watson.", EventName.TRENGER_REQUESTED)
}
