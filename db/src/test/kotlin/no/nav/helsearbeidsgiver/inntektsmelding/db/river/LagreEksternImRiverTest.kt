package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.mockEksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class LagreEksternImRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockImRepo = mockk<InntektsmeldingRepository>()

        LagreEksternImRiver(mockImRepo).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        test("lagrer ekstern inntektsmelding") {
            val innkommendeMelding = mockInnkommendeMelding()

            every { mockImRepo.lagreEksternInntektsmelding(any(), any()) } just Runs

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.EKSTERN_INNTEKTSMELDING_LAGRET.toJson(),
                    Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                    Key.FORESPOERSEL_ID to innkommendeMelding.forespoerselId.toJson(),
                )

            verifySequence {
                mockImRepo.lagreEksternInntektsmelding(innkommendeMelding.forespoerselId, innkommendeMelding.eksternInntektsmelding)
            }
        }

        test("håndterer feil") {
            val innkommendeMelding = mockInnkommendeMelding()

            val innkommendeJsonMap = innkommendeMelding.toMap()

            val forventetFail =
                Fail(
                    feilmelding = "Klarte ikke lagre ekstern inntektsmelding i database.",
                    event = innkommendeMelding.eventName,
                    transaksjonId = innkommendeMelding.transaksjonId,
                    forespoerselId = innkommendeMelding.forespoerselId,
                    utloesendeMelding = innkommendeJsonMap.toJson(),
                )

            every { mockImRepo.lagreEksternInntektsmelding(any(), any()) } throws NullPointerException()

            testRapid.sendJson(innkommendeJsonMap)

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()

            verifySequence {
                mockImRepo.lagreEksternInntektsmelding(innkommendeMelding.forespoerselId, innkommendeMelding.eksternInntektsmelding)
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
                    mockInnkommendeMelding()
                        .toMap()
                        .plus(uoensketKeyMedVerdi),
                )

                testRapid.inspektør.size shouldBeExactly 0

                verify(exactly = 0) {
                    mockImRepo.lagreEksternInntektsmelding(any(), any())
                }
            }
        }
    })

private fun mockInnkommendeMelding(): LagreEksternImMelding =
    LagreEksternImMelding(
        eventName = EventName.EKSTERN_INNTEKTSMELDING_MOTTATT,
        behovType = BehovType.LAGRE_EKSTERN_INNTEKTSMELDING,
        transaksjonId = UUID.randomUUID(),
        forespoerselId = UUID.randomUUID(),
        eksternInntektsmelding = mockEksternInntektsmelding(),
    )

private fun LagreEksternImMelding.toMap(): Map<Key, JsonElement> =
    mapOf(
        Key.EVENT_NAME to eventName.toJson(),
        Key.BEHOV to behovType.toJson(),
        Key.UUID to transaksjonId.toJson(),
        Key.DATA to
            mapOf(
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.EKSTERN_INNTEKTSMELDING to eksternInntektsmelding.toJson(EksternInntektsmelding.serializer()),
            ).toJson(),
    )

private val mockFail =
    Fail(
        feilmelding = "Get down! Get down again!",
        event = EventName.EKSTERN_INNTEKTSMELDING_MOTTATT,
        transaksjonId = UUID.randomUUID(),
        forespoerselId = UUID.randomUUID(),
        utloesendeMelding = JsonNull,
    )
