package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

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
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.test.mock.mockFail
import no.nav.hag.simba.utils.felles.test.mock.mockInntektsmeldingV1
import no.nav.hag.simba.utils.felles.test.mock.randomDigitString
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.rr.test.firstMessage
import no.nav.hag.simba.utils.rr.test.mockConnectToRapid
import no.nav.hag.simba.utils.rr.test.sendJson
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.JournalfoertInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.distribusjon.Mock.toMap
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class DistribusjonRiverTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockProducer = mockk<Producer>()

        mockConnectToRapid(testRapid) {
            listOf(
                DistribusjonRiver(mockProducer),
            )
        }

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        context("distribuerer inntektsmelding på kafka topic") {
            withData(
                mapOf(
                    "forespurt" to Mock.innkommendeMelding(),
                    "selvbestemt" to
                        Mock.innkommendeMelding().copy(
                            inntektsmelding =
                                mockInntektsmeldingV1().copy(
                                    type =
                                        Inntektsmelding.Type.Selvbestemt(
                                            id = UUID.randomUUID(),
                                        ),
                                ),
                        ),
                ),
            ) { innkommendeMelding ->
                every { mockProducer.send(any()) } just Runs

                testRapid.sendJson(innkommendeMelding.toMap())

                testRapid.inspektør.size shouldBeExactly 1

                testRapid.firstMessage().toMap() shouldContainExactly
                    mapOf(
                        Key.EVENT_NAME to EventName.INNTEKTSMELDING_DISTRIBUERT.toJson(),
                        Key.KONTEKST_ID to innkommendeMelding.kontekstId.toJson(),
                        Key.JOURNALPOST_ID to innkommendeMelding.journalpostId.toJson(),
                        Key.INNTEKTSMELDING to innkommendeMelding.inntektsmelding.toJson(Inntektsmelding.serializer()),
                    )

                verifySequence {
                    mockProducer.send(
                        JournalfoertInntektsmelding(
                            journalpostId = innkommendeMelding.journalpostId,
                            inntektsmelding = innkommendeMelding.inntektsmelding,
                        ),
                    )
                }
            }
        }

        test("håndterer når producer feiler") {
            every { mockProducer.send(any()) } throws RuntimeException("feil og feil, fru blom")

            val innkommendeMelding = Mock.innkommendeMelding()

            val innkommendeJsonMap = innkommendeMelding.toMap()

            val forventetFail =
                Fail(
                    feilmelding = "Klarte ikke distribuere IM med journalpost-ID '${innkommendeMelding.journalpostId}'.",
                    kontekstId = innkommendeMelding.kontekstId,
                    utloesendeMelding = innkommendeJsonMap,
                )

            testRapid.sendJson(innkommendeJsonMap)

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()

            verifySequence {
                mockProducer.send(any())
            }
        }

        context("ignorerer melding") {
            withData(
                mapOf(
                    "melding med ukjent event" to Pair(Key.EVENT_NAME, EventName.TILGANG_ORG_REQUESTED.toJson()),
                    "melding med behov" to Pair(Key.BEHOV, BehovType.HENT_VIRKSOMHET_NAVN.toJson()),
                    "melding med data" to Pair(Key.DATA, "".toJson()),
                    "melding med fail" to Pair(Key.FAIL, Mock.fail.toJson(Fail.serializer())),
                ),
            ) { uoensketKeyMedVerdi ->
                testRapid.sendJson(
                    Mock
                        .innkommendeMelding()
                        .toMap()
                        .plus(uoensketKeyMedVerdi),
                )

                testRapid.inspektør.size shouldBeExactly 0

                verify(exactly = 0) {
                    mockProducer.send(any())
                }
            }
        }
    })

private object Mock {
    val fail = mockFail("I'm afraid I can't let you do that.", EventName.INNTEKTSMELDING_JOURNALPOST_ID_LAGRET)

    fun innkommendeMelding(): Melding =
        Melding(
            eventName = EventName.INNTEKTSMELDING_JOURNALPOST_ID_LAGRET,
            kontekstId = UUID.randomUUID(),
            inntektsmelding = mockInntektsmeldingV1(),
            journalpostId = randomDigitString(13),
        )

    fun Melding.toMap(): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
            Key.JOURNALPOST_ID to journalpostId.toJson(),
        )
}
