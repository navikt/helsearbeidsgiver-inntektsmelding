package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.inntektsmelding.joark.tilXmlInntektsmelding
import no.seres.xsd.nav.inntektsmelding_m._20181211.ObjectFactory
import java.io.StringWriter
import javax.xml.bind.JAXBContext
import no.nav.hag.simba.utils.felles.domene.InntektsmeldingIntern as Inntektsmelding

val CONTEXT: JAXBContext = JAXBContext.newInstance(ObjectFactory::class.java)

fun transformToXML(inntektsmeldingDokument: Inntektsmelding): String {
    val inntektM = tilXmlInntektsmelding(inntektsmeldingDokument)
    val writer = StringWriter()
    CONTEXT.createMarshaller().marshal(ObjectFactory().createMelding(inntektM), writer)
    return writer.toString()
}
