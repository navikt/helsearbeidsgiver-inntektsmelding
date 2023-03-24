package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.seres.xsd.nav.inntektsmelding_m._20181211.Arbeidsgiver
import no.seres.xsd.nav.inntektsmelding_m._20181211.InntektsmeldingM
import no.seres.xsd.nav.inntektsmelding_m._20181211.Skjemainnhold

class XMLBuilder(val inntektsmelding: InntektsmeldingDokument) {

    fun build(): String { // TODO: fix me :)
        val arbeidsgiver = Arbeidsgiver()
        val xmlMapper = XmlMapper()

        xmlMapper.registerModule(JaxbAnnotationModule())
        arbeidsgiver.virksomhetsnummer = inntektsmelding.orgnrUnderenhet
        val inntektsmelding = InntektsmeldingM()
        val skjemainnhold = Skjemainnhold()
        skjemainnhold.arbeidsgiver = arbeidsgiver
        inntektsmelding.skjemainnhold = skjemainnhold
        return xmlMapper.writeValueAsString(
            inntektsmelding
        )
    }
}
