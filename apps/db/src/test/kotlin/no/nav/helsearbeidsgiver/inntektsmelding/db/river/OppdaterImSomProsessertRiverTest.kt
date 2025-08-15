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
import no.nav.helsearbeidsgiver.inntektsmelding.db.SelvbestemtImRepo
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class OppdaterImSomProsessertRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockImRepo = mockk<InntektsmeldingRepository>()
        val mockSelvbestemtImRepo = mockk<SelvbestemtImRepo>()

        OppdaterImSomProsessertRiver(mockImRepo, mockSelvbestemtImRepo).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        listOf(
            "forespurt inntektsmelding" to mockInntektsmeldingV1(),
            "selvbestemt inntektsmelding" to mockInntektsmeldingV1().copy(type = Inntektsmelding.Type.Selvbestemt(UUID.randomUUID())),
        ).forEach { (testContext, inntektsmelding) ->
            context(testContext) {

                test("oppdaterer inntektsmelding som prosessert") {
                    every { mockImRepo.oppdaterSomProsessert(any()) } just Runs
                    every { mockSelvbestemtImRepo.oppdaterSomProsessert(any()) } just Runs

                    val innkommendeMelding = innkommendeMelding(inntektsmelding)

                    testRapid.sendJson(innkommendeMelding.toMap())

                    testRapid.inspektør.size shouldBeExactly 0

                    verifySequence {
                        when (inntektsmelding.type) {
                            is Inntektsmelding.Type.Forespurt,
                            is Inntektsmelding.Type.ForespurtEkstern,
                            -> mockImRepo.oppdaterSomProsessert(innkommendeMelding.inntektsmelding.id)
                            is Inntektsmelding.Type.Selvbestemt,
                            is Inntektsmelding.Type.Fisker,
                            is Inntektsmelding.Type.UtenArbeidsforhold,
                            is Inntektsmelding.Type.Behandlingsdager,
                            -> mockSelvbestemtImRepo.oppdaterSomProsessert(innkommendeMelding.inntektsmelding.id)
                        }
                    }
                }

                test("håndterer feil") {
                    every { mockImRepo.oppdaterSomProsessert(any()) } throws NullPointerException()
                    every { mockSelvbestemtImRepo.oppdaterSomProsessert(any()) } throws NullPointerException()

                    val innkommendeMelding = innkommendeMelding(inntektsmelding)

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
                        when (inntektsmelding.type) {
                            is Inntektsmelding.Type.Forespurt, is Inntektsmelding.Type.ForespurtEkstern ->
                                mockImRepo.oppdaterSomProsessert(innkommendeMelding.inntektsmelding.id)

                            is Inntektsmelding.Type.Selvbestemt, is Inntektsmelding.Type.Fisker, is Inntektsmelding.Type.UtenArbeidsforhold,
                            is Inntektsmelding.Type.Behandlingsdager,
                            ->
                                mockSelvbestemtImRepo.oppdaterSomProsessert(innkommendeMelding.inntektsmelding.id)
                        }
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
                            innkommendeMelding(inntektsmelding)
                                .toMap()
                                .plus(uoensketKeyMedVerdi),
                        )

                        testRapid.inspektør.size shouldBeExactly 0

                        verify(exactly = 0) {
                            mockImRepo.oppdaterSomProsessert(any())
                            mockSelvbestemtImRepo.oppdaterSomProsessert(any())
                        }
                    }
                }
            }
        }
    })

private fun innkommendeMelding(inntektsmelding: Inntektsmelding): OppdaterImSomProsessertMelding =
    OppdaterImSomProsessertMelding(
        eventName = EventName.INNTEKTSMELDING_DISTRIBUERT,
        kontekstId = UUID.randomUUID(),
        inntektsmelding = inntektsmelding,
    )

private fun OppdaterImSomProsessertMelding.toMap() =
    mapOf(
        Key.EVENT_NAME to eventName.toJson(),
        Key.KONTEKST_ID to kontekstId.toJson(),
        Key.INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
    )

private val fail = mockFail("Is it supposed to make that noise?", EventName.INNTEKTSMELDING_DISTRIBUERT)
