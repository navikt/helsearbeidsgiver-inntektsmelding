package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.inntektsmelding.joark.mappers.InntektDokumentTilSkjemainnholdMapper
import no.seres.xsd.nav.inntektsmelding_m._20181211.ObjectFactory
import org.mapstruct.factory.Mappers
import java.io.StringWriter
import javax.xml.bind.JAXBContext

val CONTEXT = JAXBContext.newInstance(ObjectFactory::class.java)

fun xmlMapper(): ObjectMapper = XmlMapper().apply {
    this.registerModule(JaxbAnnotationModule())
    this.registerModule(JavaTimeModule())
    this.configure(FromXmlParser.Feature.EMPTY_ELEMENT_AS_NULL, true)
    this.enable(SerializationFeature.INDENT_OUTPUT)
}

fun transformToXML(inntektsmeldingDokument: InntektsmeldingDokument): String {
    val mapper = Mappers.getMapper(InntektDokumentTilSkjemainnholdMapper::class.java)
    val inntektM = mapper.InntektDokumentTilInntekstmeldingM(inntektsmeldingDokument)
    val writer = StringWriter()
    CONTEXT.createMarshaller().marshal(ObjectFactory().createMelding(inntektM), writer)
    return writer.toString()
}
