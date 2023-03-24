package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class XMLMapperTest {

    @Test
    @Disabled
    fun build() {
        val xmlBuilder = XMLBuilder(MockInntektsmeldingDokument())
        val xml = xmlBuilder.build()
        println(xml)
    }
}
