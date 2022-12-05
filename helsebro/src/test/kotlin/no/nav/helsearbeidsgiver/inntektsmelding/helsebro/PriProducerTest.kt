package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

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
import java.util.UUID

class PriProducerTest : FunSpec({
    val mockProducer = mockk<KafkaProducer<String, TrengerForespørsel>>()

    val priProducer = PriProducer(
        producer = mockProducer
    )

    beforeTest {
        clearAllMocks()
    }

    test("gir true ved sendt melding til kafka stream") {
        every { mockProducer.send(any()).get() } returns mockRecordMetadata()

        val trengerForespurtData = mockTrengerForespurtData()

        val bleMeldingSendt = priProducer.send(trengerForespurtData)

        bleMeldingSendt.shouldBeTrue()

        val expected = ProducerRecord<String, TrengerForespørsel>(
            "helsearbeidsgiver.pri",
            trengerForespurtData
        )

        verifySequence { mockProducer.send(expected) }
    }

    test("gir false ved feilet sending til kafka stream") {
        every { mockProducer.send(any()) } throws TimeoutException("too slow bro")

        val trengerForespurtData = mockTrengerForespurtData()

        val bleMeldingSendt = priProducer.send(trengerForespurtData)

        bleMeldingSendt.shouldBeFalse()

        verifySequence { mockProducer.send(any()) }
    }
})

private fun mockTrengerForespurtData(): TrengerForespørsel =
    TrengerForespørsel(
        orgnr = "123",
        fnr = "abc",
        UUID.randomUUID()
    )

private fun mockRecordMetadata(): RecordMetadata =
    RecordMetadata(null, 0, 0, 0, 0, 0)
