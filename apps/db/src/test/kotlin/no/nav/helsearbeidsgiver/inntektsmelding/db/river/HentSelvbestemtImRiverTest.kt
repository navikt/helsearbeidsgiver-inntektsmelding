package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.mockFail
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.db.SelvbestemtImRepo
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class HentSelvbestemtImRiverTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockSelvbestemtImRepo = mockk<SelvbestemtImRepo>()

        HentSelvbestemtImRiver(mockSelvbestemtImRepo).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        test("henter inntektsmelding") {
            val innkommendeMelding = innkommendeMelding()
            val inntektsmelding =
                mockInntektsmeldingV1().copy(
                    type =
                        Inntektsmelding.Type.Selvbestemt(
                            id = innkommendeMelding.selvbestemtId,
                        ),
                )

            every { mockSelvbestemtImRepo.hentNyesteIm(innkommendeMelding.selvbestemtId) } returns inntektsmelding

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                    Key.KONTEKST_ID to innkommendeMelding.kontekstId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.SELVBESTEMT_ID to innkommendeMelding.selvbestemtId.toJson(),
                            Key.SELVBESTEMT_INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
                        ).toJson(),
                )

            verifySequence {
                mockSelvbestemtImRepo.hentNyesteIm(innkommendeMelding.selvbestemtId)
            }
        }

        test("svarer med feil når inntektsmelding ikke finnes") {
            val innkommendeMelding = innkommendeMelding()
            val forventetFail = innkommendeMelding.toFail("Fant ikke selvbestemt inntektsmelding.")

            every { mockSelvbestemtImRepo.hentNyesteIm(innkommendeMelding.selvbestemtId) } returns null

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()

            verifySequence {
                mockSelvbestemtImRepo.hentNyesteIm(innkommendeMelding.selvbestemtId)
            }
        }

        test("håndterer at repo feiler") {
            val innkommendeMelding = innkommendeMelding()
            val forventetFail = innkommendeMelding.toFail("Klarte ikke hente selvbestemt inntektsmelding.")

            every { mockSelvbestemtImRepo.hentNyesteIm(any()) } throws RuntimeException("CAPTCHA time, baby!")

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()

            verifySequence {
                mockSelvbestemtImRepo.hentNyesteIm(innkommendeMelding.selvbestemtId)
            }
        }

        context("ignorerer melding") {
            withData(
                mapOf(
                    "melding med uønsket behov" to Pair(Key.BEHOV, BehovType.HENT_VIRKSOMHET_NAVN.toJson()),
                    "melding med data som flagg" to Pair(Key.DATA, "".toJson()),
                    "melding med fail" to Pair(Key.FAIL, mockFail.toJson(Fail.serializer())),
                ),
            ) { uoensketKeyMedVerdi ->
                testRapid.sendJson(
                    innkommendeMelding()
                        .toMap()
                        .plus(uoensketKeyMedVerdi),
                )

                testRapid.inspektør.size shouldBeExactly 0

                verify(exactly = 0) {
                    mockSelvbestemtImRepo.hentNyesteIm(any())
                }
            }
        }
    })

private fun innkommendeMelding(): HentSelvbestemtImMelding {
    val selvbestemtId = UUID.randomUUID()

    return HentSelvbestemtImMelding(
        eventName = EventName.SELVBESTEMT_IM_REQUESTED,
        behovType = BehovType.HENT_SELVBESTEMT_IM,
        kontekstId = UUID.randomUUID(),
        data =
            mapOf(
                Key.SELVBESTEMT_ID to selvbestemtId.toJson(),
            ),
        selvbestemtId = selvbestemtId,
    )
}

private fun HentSelvbestemtImMelding.toMap(): Map<Key, JsonElement> =
    mapOf(
        Key.EVENT_NAME to eventName.toJson(),
        Key.BEHOV to behovType.toJson(),
        Key.KONTEKST_ID to kontekstId.toJson(),
        Key.DATA to data.toJson(),
    )

private fun HentSelvbestemtImMelding.toFail(feilmelding: String): Fail =
    Fail(
        feilmelding = feilmelding,
        kontekstId = kontekstId,
        utloesendeMelding = toMap(),
    )

private val mockFail = mockFail("Computer says no.", EventName.SELVBESTEMT_IM_REQUESTED)
