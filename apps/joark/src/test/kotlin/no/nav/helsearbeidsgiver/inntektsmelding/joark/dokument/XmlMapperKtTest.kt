package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.hag.simba.utils.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.joark.tilXmlInntektsmelding
import no.nav.helsearbeidsgiver.utils.test.date.oktober
import org.junit.jupiter.api.Test
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.validation.SchemaFactory

class XmlMapperKtTest {
    @Test
    fun `Bør generere xml fra InnsendingM dokument`() {
        val mockInntektsmelding = mockInntektsmeldingV1()
        mapToXML(mockInntektsmelding)
    }

    @Test
    fun `tom arbeidsgviverPeriodelister i dokument skal bli null`() {
        mapToXML(mockInntektsmeldingDokumentMedTommeLister())
    }

    private fun mapToXML(mockInntektsmelding: Inntektsmelding) {
        val inntektM = tilXmlInntektsmelding(mockInntektsmelding, 20.oktober)
        val sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
        val xsdSchema = sf.newSchema(inntektM.javaClass.classLoader.getResource("xsd/Inntektsmelding20181211_V7.xsd"))
        val xml = transformToXml(mockInntektsmelding, 20.oktober)
        val stringReader = StringReader(xml)
        val unmarshaller = CONTEXT.createUnmarshaller()
        unmarshaller.schema = xsdSchema
        unmarshaller.unmarshal(stringReader)
        println(xml)
    }
}

private fun mockInntektsmeldingDokumentMedTommeLister(): Inntektsmelding =
    mockInntektsmeldingV1().let {
        it.copy(
            agp =
                it.agp?.copy(
                    perioder = emptyList(),
                ),
            naturalytelser = emptyList(),
            refusjon =
                it.refusjon?.copy(
                    endringer = emptyList(),
                ),
        )
    }
