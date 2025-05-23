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
import kotlinx.serialization.json.JsonObject
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
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class OppdaterImSomProsessertRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockImRepo = mockk<InntektsmeldingRepository>()

        OppdaterImSomProsessertRiver(mockImRepo).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        test("oppdaterer inntektsmelding som prosessert") {
            every { mockImRepo.oppdaterSomProsessert(any()) } just Runs

            val innkommendeMelding = innkommendeMelding()

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 0

            verifySequence {
                mockImRepo.oppdaterSomProsessert(innkommendeMelding.inntektsmelding.id)
            }
        }

        test("håndterer feil") {
            every { mockImRepo.oppdaterSomProsessert(any()) } throws NullPointerException()

            val innkommendeMelding = innkommendeMelding()

            val forventetFail =
                Fail(
                    feilmelding = "Klarte ikke markere inntektsmelding som prosessert i database.",
                    kontekstId = innkommendeMelding.kontekstId,
                    utloesendeMelding = innkommendeMelding.toMap(),
                )

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()

            verifySequence {
                mockImRepo.oppdaterSomProsessert(innkommendeMelding.inntektsmelding.id)
            }
        }

        context("ignorerer melding") {
            withData(
                mapOf(
                    "melding med behov" to Pair(Key.BEHOV, BehovType.TILGANGSKONTROLL.toJson()),
                    "melding med data" to Pair(Key.DATA, JsonObject(emptyMap())),
                    "melding med fail" to Pair(Key.FAIL, fail.toJson(Fail.serializer())),
                ),
            ) { uoensketKeyMedVerdi ->
                testRapid.sendJson(
                    innkommendeMelding()
                        .toMap()
                        .plus(uoensketKeyMedVerdi),
                )

                testRapid.inspektør.size shouldBeExactly 0

                verify(exactly = 0) {
                    mockImRepo.oppdaterSomProsessert(any())
                }
            }
        }
    })

private fun innkommendeMelding(): OppdaterImSomProsessertMelding =
    OppdaterImSomProsessertMelding(
        eventName = EventName.INNTEKTSMELDING_DISTRIBUERT,
        kontekstId = UUID.randomUUID(),
        inntektsmelding = mockInntektsmeldingV1(),
    )

private fun OppdaterImSomProsessertMelding.toMap() =
    mapOf(
        Key.EVENT_NAME to eventName.toJson(),
        Key.KONTEKST_ID to kontekstId.toJson(),
        Key.INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
    )

private val fail = mockFail("Is it supposed to make that noise?", EventName.INNTEKTSMELDING_DISTRIBUERT)
