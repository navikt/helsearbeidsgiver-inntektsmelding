package no.nav.helsearbeidsgiver.felles.kafka

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.kafka.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.test.kafka.mockRecordMetadata
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.TimeoutException

class ProducerTest :
    FunSpec({
        val mockProducer = mockk<KafkaProducer<String, JsonElement>>()

        val producer =
            Producer(
                producer = mockProducer,
            )

        beforeEach {
            clearAllMocks()
        }

        context("send<JsonElement>") {

            test("gir suksessobjekt ved sendt melding til kafka stream") {
                every { mockProducer.send(any()).get() } returns mockRecordMetadata()

                val expectedMessageJson =
                    mapOf(
                        Pri.Key.NOTIS to "universet er what?".toJson(),
                        Pri.Key.FORESPOERSEL_ID to "8800664422".toJson(),
                    ).toJson()

                val result = producer.send(expectedMessageJson)

                result.isSuccess.shouldBeTrue()
                result.getOrNull() shouldBe expectedMessageJson

                val expected =
                    ProducerRecord<String, JsonElement>(
                        Pri.TOPIC,
                        expectedMessageJson,
                    )

                verifySequence { mockProducer.send(expected) }
            }

            test("gir feilobjekt ved feilet sending til kafka stream") {
                every { mockProducer.send(any()) } throws TimeoutException("too slow bro")

                val expectedMessageJson =
                    mapOf(
                        Pri.Key.NOTIS to "universet er flatt".toJson(),
                        Pri.Key.FORESPOERSEL_ID to "5577991133".toJson(),
                    ).toJson()

                val result = producer.send(expectedMessageJson)

                result.isFailure.shouldBeTrue()
                result.getOrNull() shouldBe null

                verifySequence { mockProducer.send(any()) }
            }
        }

        context("send<vararg Pair<Pri.Key, JsonElement>>") {

            test("gir suksessobjekt ved sendt melding til kafka stream") {
                every { mockProducer.send(any()).get() } returns mockRecordMetadata()

                val expectedMessage =
                    mapOf(
                        Pri.Key.BEHOV to "sol".toJson(),
                        Pri.Key.BOOMERANG to "\uD83E\uDE83".toJson(),
                    )

                val result =
                    producer.send(
                        *expectedMessage.toList().toTypedArray(),
                    )

                result.isSuccess.shouldBeTrue()
                result.getOrNull() shouldBe expectedMessage.toJson()

                val expected =
                    ProducerRecord<String, JsonElement>(
                        Pri.TOPIC,
                        expectedMessage.toJson(),
                    )

                verifySequence { mockProducer.send(expected) }
            }

            test("gir feilobjekt ved feilet sending til kafka stream") {
                every { mockProducer.send(any()) } throws TimeoutException("too slow bro")

                val expectedMessage =
                    mapOf(
                        Pri.Key.BEHOV to "måne".toJson(),
                        Pri.Key.BOOMERANG to "\uD83E\uDE83".toJson(),
                    )

                val result =
                    producer.send(
                        *expectedMessage.toList().toTypedArray(),
                    )

                result.isFailure.shouldBeTrue()
                result.getOrNull() shouldBe null

                verifySequence { mockProducer.send(any()) }
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
