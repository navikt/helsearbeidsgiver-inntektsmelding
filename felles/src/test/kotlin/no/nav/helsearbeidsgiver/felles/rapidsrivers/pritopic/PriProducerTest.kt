package no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.errors.TimeoutException

class PriProducerTest : FunSpec({
    val mockProducer = mockk<KafkaProducer<String, Food>>()

    val priProducer = PriProducer(
        producer = mockProducer
    )

    beforeEach {
        clearAllMocks()
    }

    test("gir true ved sendt melding til kafka stream") {
        every { mockProducer.send(any()).get() } returns mockRecordMetadata()

        val food = mockFood()

        val bleMeldingSendt = priProducer.send(food)

        bleMeldingSendt.shouldBeTrue()

        val expected = ProducerRecord<String, Food>(
            Pri.TOPIC,
            food
        )

        verifySequence { mockProducer.send(expected) }
    }

    test("gir false ved feilet sending til kafka stream") {
        every { mockProducer.send(any()) } throws TimeoutException("too slow bro")

        val food = mockFood()

        val bleMeldingSendt = priProducer.send(food)

        bleMeldingSendt.shouldBeFalse()

        verifySequence { mockProducer.send(any()) }
    }
})

private fun mockRecordMetadata(): RecordMetadata =
    RecordMetadata(null, 0, 0, 0, 0, 0)

private class Food(
    val name: String,
    val deliciousness: Double,
    val dailyRecommendedIntake: Int
)

private fun mockFood(): Food =
    Food(
        name = "Taco",
        deliciousness = 9.8,
        dailyRecommendedIntake = 100
    )
