package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.TrengerForespoersel
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.errors.TimeoutException

class PriProducerTest : FunSpec({
    val mockProducer = mockk<KafkaProducer<String, TrengerForespoersel>>()

    val priProducer = PriProducer(
        producer = mockProducer
    )

    beforeTest {
        clearAllMocks()
    }

    test("gir true ved sendt melding til kafka stream") {
        every { mockProducer.send(any()).get() } returns mockRecordMetadata()

        val trengerForespoersel = mockTrengerForespoersel()

        val bleMeldingSendt = priProducer.send(trengerForespoersel)

        bleMeldingSendt.shouldBeTrue()

        val expected = ProducerRecord<String, TrengerForespoersel>(
            Pri.NAME,
            trengerForespoersel
        )

        verifySequence { mockProducer.send(expected) }
    }

    test("gir false ved feilet sending til kafka stream") {
        every { mockProducer.send(any()) } throws TimeoutException("too slow bro")

        val trengerForespoersel = mockTrengerForespoersel()

        val bleMeldingSendt = priProducer.send(trengerForespoersel)

        bleMeldingSendt.shouldBeFalse()

        verifySequence { mockProducer.send(any()) }
    }
})

private fun mockRecordMetadata(): RecordMetadata =
    RecordMetadata(null, 0, 0, 0, 0, 0)
