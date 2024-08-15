package no.nav.helsearbeidsgiver.inntektsmelding.db.river

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
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Arbeidsgiverperiode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingGammeltFormat
import no.nav.helsearbeidsgiver.felles.test.mock.mockSkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.juli
import java.util.UUID

class LagreImSkjemaRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockInntektsmeldingRepo = mockk<InntektsmeldingRepository>()

        LagreImSkjemaRiver(mockInntektsmeldingRepo).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        val rentInntektsmeldingSkjema = mockSkjemaInntektsmelding()
        val justertInntektsmeldingSkjema =
            rentInntektsmeldingSkjema.let { skjema ->
                skjema.copy(
                    agp =
                        skjema.agp?.let { agp ->
                            Arbeidsgiverperiode(
                                perioder = agp.perioder,
                                egenmeldinger = listOf(13.juli til 31.juli),
                                redusertLoennIAgp = agp.redusertLoennIAgp,
                            )
                        },
                )
            }

        val renInntektsmelding = mockInntektsmeldingGammeltFormat()
        val justertInntektsmelding =
            renInntektsmelding
                .copy(egenmeldingsperioder = listOf(13.juli til 31.juli))

        context("inntektsmeldingskjema lagres") {
            withData(
                mapOf(
                    "hvis ingen andre inntektsmeldingskjemaer eller inntektsmeldinger er mottatt" to
                        EksisterendeInnsendinger(
                            eksisterendeSkjema = null,
                            eksisterendeInntektsmelding = null,
                        ),
                    "hvis ikke duplikat av siste inntektsmeldingskjema og ingen inntektsmeldinger er mottatt" to
                        EksisterendeInnsendinger(
                            eksisterendeSkjema = justertInntektsmeldingSkjema,
                            eksisterendeInntektsmelding = null,
                        ),
                    "hvis ingen inntektsmeldingskjema mottatt og ikke duplikat av siste inntektsmelding" to
                        EksisterendeInnsendinger(
                            eksisterendeSkjema = null,
                            eksisterendeInntektsmelding = justertInntektsmelding,
                        ),
                    "hvis ikke duplikat av siste inntektsmeldingskjema og heller ikke duplikat av siste inntektsmelding" to
                        EksisterendeInnsendinger(
                            eksisterendeSkjema = justertInntektsmeldingSkjema,
                            eksisterendeInntektsmelding = justertInntektsmelding,
                        ),
                    "hvis ikke duplikat av siste inntektsmeldingskjema selv om den er duplikat av siste inntektsmelding" to
                        EksisterendeInnsendinger(
                            eksisterendeSkjema = justertInntektsmeldingSkjema,
                            eksisterendeInntektsmelding = renInntektsmelding,
                        ),
                ),
            ) { eksisterendeInnsendinger ->
                every { mockInntektsmeldingRepo.hentNyesteInntektsmeldingSkjema(any()) } returns eksisterendeInnsendinger.eksisterendeSkjema
                every { mockInntektsmeldingRepo.hentNyesteInntektsmelding(any()) } returns eksisterendeInnsendinger.eksisterendeInntektsmelding
                every { mockInntektsmeldingRepo.lagreInntektsmeldingSkjema(any(), any()) } just Runs

                val nyttInntektsmeldingSkjema = mockSkjemaInntektsmelding()

                val innkommendeMelding = innkommendeMelding(nyttInntektsmeldingSkjema)

                testRapid.sendJson(innkommendeMelding.toMap())

                testRapid.inspektør.size shouldBeExactly 1

                testRapid.firstMessage().toMap() shouldContainExactly
                    mapOf(
                        Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                        Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                        Key.DATA to
                            mapOf(
                                Key.FORESPOERSEL_ID to innkommendeMelding.forespoerselId.toJson(),
                                Key.SKJEMA_INNTEKTSMELDING to innkommendeMelding.inntektsmeldingSkjema.toJson(SkjemaInntektsmelding.serializer()),
                                Key.ER_DUPLIKAT_IM to false.toJson(Boolean.serializer()),
                            ).toJson(),
                    )

                verifySequence {
                    mockInntektsmeldingRepo.hentNyesteInntektsmelding(innkommendeMelding.forespoerselId)
                    mockInntektsmeldingRepo.hentNyesteInntektsmeldingSkjema(innkommendeMelding.forespoerselId)
                    mockInntektsmeldingRepo.lagreInntektsmeldingSkjema(innkommendeMelding.forespoerselId, nyttInntektsmeldingSkjema)
                }
            }
        }

        context("inntektsmeldingskjema lagres ikke, men svarer OK") {
            withData(
                mapOf(
                    "hvis duplikat av siste inntektsmeldingskjema og ingen inntektsmeldinger er mottatt" to
                        EksisterendeInnsendinger(
                            eksisterendeSkjema = rentInntektsmeldingSkjema,
                            eksisterendeInntektsmelding = null,
                        ),
                    "hvis ingen inntektsmeldingskjema mottatt og duplikat av siste inntektsmelding" to
                        EksisterendeInnsendinger(
                            eksisterendeSkjema = null,
                            eksisterendeInntektsmelding = renInntektsmelding,
                        ),
                    "hvis duplikat av siste inntektsmeldingskjema og duplikat av siste inntektsmelding" to
                        EksisterendeInnsendinger(
                            eksisterendeSkjema = rentInntektsmeldingSkjema,
                            eksisterendeInntektsmelding = renInntektsmelding,
                        ),
                    "hvis duplikat av siste inntektsmeldingskjema men ikke duplikat av siste inntektsmelding" to
                        EksisterendeInnsendinger(
                            eksisterendeSkjema = rentInntektsmeldingSkjema,
                            eksisterendeInntektsmelding = justertInntektsmelding,
                        ),
                ),
            ) { eksisterendeInnsendinger ->
                every { mockInntektsmeldingRepo.hentNyesteInntektsmeldingSkjema(any()) } returns eksisterendeInnsendinger.eksisterendeSkjema
                every { mockInntektsmeldingRepo.hentNyesteInntektsmelding(any()) } returns eksisterendeInnsendinger.eksisterendeInntektsmelding
                every { mockInntektsmeldingRepo.lagreInntektsmeldingSkjema(any(), any()) } just Runs

                val innkommendeMelding = innkommendeMelding(rentInntektsmeldingSkjema)

                testRapid.sendJson(innkommendeMelding.toMap())

                testRapid.inspektør.size shouldBeExactly 1

                testRapid.firstMessage().toMap() shouldContainExactly
                    mapOf(
                        Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                        Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                        Key.DATA to
                            mapOf(
                                Key.FORESPOERSEL_ID to innkommendeMelding.forespoerselId.toJson(),
                                Key.SKJEMA_INNTEKTSMELDING to innkommendeMelding.inntektsmeldingSkjema.toJson(SkjemaInntektsmelding.serializer()),
                                Key.ER_DUPLIKAT_IM to true.toJson(Boolean.serializer()),
                            ).toJson(),
                    )

                verifySequence {
                    mockInntektsmeldingRepo.hentNyesteInntektsmelding(innkommendeMelding.forespoerselId)
                    mockInntektsmeldingRepo.hentNyesteInntektsmeldingSkjema(innkommendeMelding.forespoerselId)
                }
                verify(exactly = 0) {
                    mockInntektsmeldingRepo.lagreInntektsmeldingSkjema(innkommendeMelding.forespoerselId, rentInntektsmeldingSkjema)
                }
            }
        }

        test("håndterer at repo feiler") {
            every { mockInntektsmeldingRepo.hentNyesteInntektsmelding(any()) } returns null

            every {
                mockInntektsmeldingRepo.hentNyesteInntektsmeldingSkjema(any())
            } throws RuntimeException("Tråbbel med den Rolls-Royce? Den jo vere garantert!")

            val innkommendeMelding = innkommendeMelding()

            val forventetFail =
                Fail(
                    feilmelding = "Klarte ikke lagre inntektsmeldingskjema i database.",
                    event = innkommendeMelding.eventName,
                    transaksjonId = innkommendeMelding.transaksjonId,
                    forespoerselId = innkommendeMelding.forespoerselId,
                    utloesendeMelding = innkommendeMelding.toMap().toJson(),
                )

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()

            verifySequence {
                mockInntektsmeldingRepo.hentNyesteInntektsmelding(any())
                mockInntektsmeldingRepo.hentNyesteInntektsmeldingSkjema(any())
            }
            verify(exactly = 0) {
                mockInntektsmeldingRepo.lagreInntektsmeldingSkjema(any(), any())
            }
        }
        context("ignorerer melding") {
            withData(
                mapOf(
                    "hvis den inneholder uønsket behov" to Pair(Key.BEHOV, BehovType.HENT_VIRKSOMHET_NAVN.toJson()),
                    "hvis den inneholder data som flagg" to Pair(Key.DATA, "".toJson()),
                    "hvis den inneholder en fail" to Pair(Key.FAIL, mockFail.toJson(Fail.serializer())),
                ),
            ) { uoensketKeyMedVerdi ->
                testRapid.sendJson(
                    innkommendeMelding()
                        .toMap()
                        .plus(uoensketKeyMedVerdi),
                )

                testRapid.inspektør.size shouldBeExactly 0

                verify(exactly = 0) {
                    mockInntektsmeldingRepo.hentNyesteInntektsmeldingSkjema(any())
                    mockInntektsmeldingRepo.hentNyesteInntektsmelding(any())
                    mockInntektsmeldingRepo.lagreInntektsmeldingSkjema(any(), any())
                }
            }
        }
    })

private fun innkommendeMelding(inntektsmeldingSkjema: SkjemaInntektsmelding = mockSkjemaInntektsmelding()): LagreImSkjemaMelding {
    val forespoerselId = UUID.randomUUID()

    return LagreImSkjemaMelding(
        eventName = EventName.INSENDING_STARTED,
        behovType = BehovType.LAGRE_IM_SKJEMA,
        transaksjonId = UUID.randomUUID(),
        data =
            mapOf(
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.SKJEMA_INNTEKTSMELDING to inntektsmeldingSkjema.toJson(SkjemaInntektsmelding.serializer()),
            ),
        forespoerselId = forespoerselId,
        inntektsmeldingSkjema = inntektsmeldingSkjema,
    )
}

private fun LagreImSkjemaMelding.toMap(): Map<Key, JsonElement> =
    mapOf(
        Key.EVENT_NAME to eventName.toJson(),
        Key.BEHOV to behovType.toJson(),
        Key.UUID to transaksjonId.toJson(),
        Key.DATA to data.toJson(),
    )

private val mockFail =
    Fail(
        feilmelding = "Jai mange penga, do raka blak",
        event = EventName.INSENDING_STARTED,
        transaksjonId = UUID.randomUUID(),
        forespoerselId = UUID.randomUUID(),
        utloesendeMelding = JsonNull,
    )

data class EksisterendeInnsendinger(
    val eksisterendeSkjema: SkjemaInntektsmelding?,
    val eksisterendeInntektsmelding: Inntektsmelding?,
)
