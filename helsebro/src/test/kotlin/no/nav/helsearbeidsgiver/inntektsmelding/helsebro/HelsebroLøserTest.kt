package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class HelsebroLøserTest {
    private val loggerSikkerCollector = ListAppender<ILoggingEvent>().also {
        (loggerSikker as Logger).addAppender(it)
        it.start()
    }

    private val testRapid = TestRapid()

    init {
        HelsebroLøser(testRapid)
    }

    @Test
    fun `Løser mottar melding om mottatt forespørsel`() {
        val event = mapOf(
            "eventType" to "FORESPØRSEL_MOTTATT",
            "orgnr" to "123",
            "fnr" to "abc"
        )
            .let(JsonMessage::newMessage)
            .toJson()

        testRapid.sendTestMessage(event)

        Assertions.assertEquals(2, loggerSikkerCollector.list.size)
        loggerSikkerCollector.list.single {
            it.message.contains("Mottok melding:")
        }
    }
}
