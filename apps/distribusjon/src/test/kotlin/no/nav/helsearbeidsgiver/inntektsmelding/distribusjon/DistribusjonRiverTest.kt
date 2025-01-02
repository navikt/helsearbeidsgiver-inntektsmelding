package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

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
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.JournalfoertInntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.mockFail
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.felles.test.mock.randomDigitString
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.distribusjon.Mock.toMap
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.UUID
import java.util.concurrent.CompletableFuture

class DistribusjonRiverTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockKafkaProducer = mockk<KafkaProducer<String, String>>()

        DistribusjonRiver(mockKafkaProducer).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        test("distribuerer inntektsmelding på kafka topic") {
            every { mockKafkaProducer.send(any()) } returns CompletableFuture()

            val innkommendeMelding = Mock.innkommendeMelding()

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.INNTEKTSMELDING_DISTRIBUERT.toJson(),
                    Key.KONTEKST_ID to innkommendeMelding.transaksjonId.toJson(),
                    Key.JOURNALPOST_ID to innkommendeMelding.journalpostId.toJson(),
                    Key.INNTEKTSMELDING to innkommendeMelding.inntektsmelding.toJson(Inntektsmelding.serializer()),
                )

            val forventetRecord =
                ProducerRecord<String, String>(
                    TOPIC_HELSEARBEIDSGIVER_INNTEKTSMELDING_EKSTERN,
                    JournalfoertInntektsmelding(
                        journalpostId = innkommendeMelding.journalpostId,
                        inntektsmelding = innkommendeMelding.inntektsmelding,
                    ).toJsonStr(JournalfoertInntektsmelding.serializer()),
                )

            verifySequence {
                mockKafkaProducer.send(forventetRecord)
            }
        }

        test("distribuerer selvbestemt inntektsmelding på kafka topic") {
            every { mockKafkaProducer.send(any()) } returns CompletableFuture()

            val selvbestemtInntektsmelding =
                mockInntektsmeldingV1().copy(
                    type =
                        Inntektsmelding.Type.Selvbestemt(
                            id = UUID.randomUUID(),
                        ),
                )

            val innkommendeMelding = Mock.innkommendeMelding()

            testRapid.sendJson(
                innkommendeMelding
                    .toMap()
                    .plus(Key.INNTEKTSMELDING to selvbestemtInntektsmelding.toJson(Inntektsmelding.serializer())),
            )

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.INNTEKTSMELDING_DISTRIBUERT.toJson(),
                    Key.KONTEKST_ID to innkommendeMelding.transaksjonId.toJson(),
                    Key.JOURNALPOST_ID to innkommendeMelding.journalpostId.toJson(),
                    Key.INNTEKTSMELDING to selvbestemtInntektsmelding.toJson(Inntektsmelding.serializer()),
                )

            val forventetRecord =
                ProducerRecord<String, String>(
                    TOPIC_HELSEARBEIDSGIVER_INNTEKTSMELDING_EKSTERN,
                    JournalfoertInntektsmelding(
                        journalpostId = innkommendeMelding.journalpostId,
                        inntektsmelding = selvbestemtInntektsmelding,
                    ).toJsonStr(JournalfoertInntektsmelding.serializer()),
                )

            verifySequence {
                mockKafkaProducer.send(forventetRecord)
            }
        }

        test("håndterer når producer feiler") {
            every { mockKafkaProducer.send(any()) } throws RuntimeException("feil og feil, fru blom")

            val innkommendeMelding = Mock.innkommendeMelding()

            val innkommendeJsonMap = innkommendeMelding.toMap()

            val forventetFail =
                Fail(
                    feilmelding = "Klarte ikke distribuere IM med journalpost-ID '${innkommendeMelding.journalpostId}'.",
                    kontekstId = innkommendeMelding.transaksjonId,
                    utloesendeMelding = innkommendeJsonMap,
                )

            testRapid.sendJson(innkommendeJsonMap)

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()

            verifySequence {
                mockKafkaProducer.send(any())
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
                    mockKafkaProducer.send(any())
                }
            }
        }
    })

private object Mock {
    val fail = mockFail("I'm afraid I can't let you do that.", EventName.INNTEKTSMELDING_JOURNALPOST_ID_LAGRET)

    fun innkommendeMelding(): Melding =
        Melding(
            eventName = EventName.INNTEKTSMELDING_JOURNALPOST_ID_LAGRET,
            transaksjonId = UUID.randomUUID(),
            inntektsmelding = mockInntektsmeldingV1(),
            journalpostId = randomDigitString(13),
        )

    fun Melding.toMap(): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.KONTEKST_ID to transaksjonId.toJson(),
            Key.INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
            Key.JOURNALPOST_ID to journalpostId.toJson(),
        )
}
