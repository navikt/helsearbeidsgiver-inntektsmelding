package no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt

import io.mockk.every
import io.mockk.mockk
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.inntektsmelding.api.TestData
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

internal class PreutfyltProducerTest {

    private val GYLDIG = PreutfyltRequest(TestData.validOrgNr, TestData.validIdentitetsnummer)

    @Test
    fun skal_returnere_uuid() {
        val rapidsConnection = mockk<RapidsConnection>()
        val producer = PreutfyltProducer(rapidsConnection)
        every {
            rapidsConnection.publish(TestData.validIdentitetsnummer, any())
        } returns Unit
        assertNotNull(producer.publish(GYLDIG))
    }
}
