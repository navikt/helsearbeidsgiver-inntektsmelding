package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingDokument
import no.nav.helsearbeidsgiver.inntektsmelding.joark.mappers.InntektDokumentTilSkjemainnholdMapper
import org.junit.jupiter.api.Test
import org.mapstruct.factory.Mappers
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.validation.SchemaFactory

class XMLMapperTest {

    @Test
    fun `Bør generere xml fra InnsendingM dokument`() {
        val mockInntektsmeldingDokument = mockInntektsmeldingDokument()
        mapToXML(mockInntektsmeldingDokument)
    }

    @Test
    fun `tom arbeidsgviverPeriodelister i dokument skal bli null`() {
        mapToXML(mockInntektsmeldingDokumentMedTommeLister())
    }
    private fun mapToXML(mockInntektsmeldingDokument: InntektsmeldingDokument) {
        val mapper = Mappers.getMapper(InntektDokumentTilSkjemainnholdMapper::class.java)
        val sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
        val inntektM = mapper.InntektDokumentTilInntekstmeldingM(mockInntektsmeldingDokument)
        val xsdSchema = sf.newSchema(inntektM.javaClass.classLoader.getResource("xsd/Inntektsmelding20181211_V7.xsd"))
        val xml = transformToXML(mockInntektsmeldingDokument)
        val stringReader = StringReader(xml)
        val unmarshaller = CONTEXT.createUnmarshaller()
        unmarshaller.schema = xsdSchema
        unmarshaller.unmarshal(stringReader)
        println(xml)
    }
}
