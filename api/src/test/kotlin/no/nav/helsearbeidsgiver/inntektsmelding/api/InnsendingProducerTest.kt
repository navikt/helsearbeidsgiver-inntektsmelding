package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.mockk.every
import io.mockk.mockk
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.inntektsmelding.api.innsending.GYLDIG
import no.nav.helsearbeidsgiver.inntektsmelding.api.innsending.InnsendingProducer
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

internal class InnsendingProducerTest {

    @Test
    fun skal_returnere_uuid() {
        val rapidsConnection = mockk<RapidsConnection>()
        val producer = InnsendingProducer(rapidsConnection)
        every {
            rapidsConnection.publish(TestData.validIdentitetsnummer, any())
        } returns Unit
        assertNotNull(producer.publish("", GYLDIG))
    }
}
