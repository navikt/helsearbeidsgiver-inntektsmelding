package no.nav.helsearbeidsgiver.felles.kafka

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.JournalfoertInntektsmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.kafka.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.test.kafka.mockRecordMetadata
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.felles.test.mock.randomDigitString
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.TimeoutException
import java.util.UUID

private const val TEST_TOPIC = "test-topic"

class ProducerTest :
    FunSpec({
        val mockKafkaProducer = mockk<KafkaProducer<String, JsonElement>>()
        val producer = Producer(TEST_TOPIC, mockKafkaProducer)

        beforeEach {
            clearAllMocks()
        }

        context("send<UUID, Map<Key, JsonElement>>") {

            test("sender melding til kafka stream") {
                every { mockKafkaProducer.send(any()).get() } returns mockRecordMetadata()

                val expectedKey = UUID.randomUUID()
                val expectedMessage =
                    mapOf(
                        Key.FAIL to "universet er what?".toJson(),
                        Key.FORESPOERSEL_ID to expectedKey.toJson(),
                    )

                producer.send(expectedKey, expectedMessage)

                val expected =
                    ProducerRecord(
                        TEST_TOPIC,
                        expectedKey.toString(),
                        expectedMessage.toJson(),
                    )

                verifySequence { mockKafkaProducer.send(expected) }
            }

            test("kaster exception når sending til kafka stream feiler") {
                val forespoerselId = UUID.randomUUID()

                every { mockKafkaProducer.send(any()) } throws TimeoutException("too slow bro")

                val expectedMessage =
                    mapOf(
                        Key.FAIL to "universet er flatt".toJson(),
                        Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    )

                shouldThrowExactly<TimeoutException> {
                    producer.send(forespoerselId, expectedMessage)
                }

                verifySequence { mockKafkaProducer.send(any()) }
            }
        }

        context("send<Fnr, Map<Key, JsonElement>>") {

            test("sender melding til kafka stream") {
                every { mockKafkaProducer.send(any()).get() } returns mockRecordMetadata()

                val expectedKey = Fnr.genererGyldig()
                val expectedMessage =
                    mapOf(
                        Key.INNTEKT to "tellus er what?".toJson(),
                        Key.SAK_ID to randomDigitString(7).toJson(),
                    )

                producer.send(expectedKey, expectedMessage)

                val expected =
                    ProducerRecord(
                        TEST_TOPIC,
                        expectedKey.verdi,
                        expectedMessage.toJson(),
                    )

                verifySequence { mockKafkaProducer.send(expected) }
            }

            test("kaster exception når sending til kafka stream feiler") {
                every { mockKafkaProducer.send(any()) } throws TimeoutException("too slow bro")

                val expectedMessage =
                    mapOf(
                        Key.INNTEKT to "tellus er våt".toJson(),
                        Key.SAK_ID to randomDigitString(11).toJson(),
                    )

                shouldThrowExactly<TimeoutException> {
                    producer.send(Fnr.genererGyldig(), expectedMessage)
                }

                verifySequence { mockKafkaProducer.send(any()) }
            }
        }

        context("send<UUID, Map<Pri.Key, JsonElement>>") {

            test("sender melding til kafka stream") {
                every { mockKafkaProducer.send(any()).get() } returns mockRecordMetadata()

                val expectedKey = UUID.randomUUID()
                val expectedMessage =
                    mapOf(
                        Pri.Key.BEHOV to "sol".toJson(),
                        Pri.Key.BOOMERANG to "\uD83E\uDE83".toJson(),
                    )

                producer.send(expectedKey, expectedMessage)

                val expected =
                    ProducerRecord(
                        TEST_TOPIC,
                        expectedKey.toString(),
                        expectedMessage.toJson(),
                    )

                verifySequence { mockKafkaProducer.send(expected) }
            }

            test("kaster exception når sending til kafka stream feiler") {
                every { mockKafkaProducer.send(any()) } throws TimeoutException("too slow bro")

                val expectedMessage =
                    mapOf(
                        Pri.Key.BEHOV to "måne".toJson(),
                        Pri.Key.BOOMERANG to "\uD83E\uDE83".toJson(),
                    )

                shouldThrowExactly<TimeoutException> {
                    producer.send(UUID.randomUUID(), expectedMessage)
                }

                verifySequence { mockKafkaProducer.send(any()) }
            }
        }

        context("send<JournalfoertInntektsmelding>") {

            test("sender melding til kafka stream") {
                every { mockKafkaProducer.send(any()).get() } returns mockRecordMetadata()

                val inntektsmelding =
                    JournalfoertInntektsmelding(
                        journalpostId = randomDigitString(12),
                        inntektsmelding = mockInntektsmeldingV1(),
                    )

                producer.send(inntektsmelding)

                val expected =
                    ProducerRecord(
                        TEST_TOPIC,
                        inntektsmelding.inntektsmelding.type.id
                            .toString(),
                        inntektsmelding.toJson(JournalfoertInntektsmelding.serializer()),
                    )

                verifySequence { mockKafkaProducer.send(expected) }
            }

            test("kaster exception når sending til kafka stream feiler") {
                every { mockKafkaProducer.send(any()) } throws TimeoutException("too slow bro")

                val inntektsmelding =
                    JournalfoertInntektsmelding(
                        journalpostId = randomDigitString(11),
                        inntektsmelding = mockInntektsmeldingV1(),
                    )

                shouldThrowExactly<TimeoutException> {
                    producer.send(inntektsmelding)
                }

                verifySequence { mockKafkaProducer.send(any()) }
            }
        }
    })

private fun Map<Pri.Key, JsonElement>.toJson(): JsonElement =
    toJson(
        MapSerializer(
            Pri.Key.serializer(),
            JsonElement.serializer(),
        ),
    )
