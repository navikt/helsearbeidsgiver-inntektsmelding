package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule

fun xmlMapper(): ObjectMapper = XmlMapper().apply {
    this.registerModule(JaxbAnnotationModule())
    this.registerModule(JavaTimeModule())
    this.configure(FromXmlParser.Feature.EMPTY_ELEMENT_AS_NULL, true)
    this.enable(SerializationFeature.INDENT_OUTPUT)
}
