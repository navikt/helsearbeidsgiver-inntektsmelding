package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import jakarta.xml.bind.JAXBContext
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.joark.tilXmlInntektsmelding
import no.seres.xsd.nav.inntektsmelding_m._20181211.ObjectFactory
import java.io.StringWriter
import java.time.LocalDate

val CONTEXT: JAXBContext = JAXBContext.newInstance(ObjectFactory::class.java)

fun transformToXml(
    inntektsmeldingDokument: Inntektsmelding,
    bestemmendeFravaersdag: LocalDate,
): String {
    val inntektM = tilXmlInntektsmelding(inntektsmeldingDokument, bestemmendeFravaersdag)
    val writer = StringWriter()
    CONTEXT.createMarshaller().marshal(ObjectFactory().createMelding(inntektM), writer)
    return writer.toString()
}
