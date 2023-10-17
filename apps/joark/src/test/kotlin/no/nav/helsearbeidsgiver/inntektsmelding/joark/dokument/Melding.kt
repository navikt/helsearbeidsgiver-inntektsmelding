package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import no.seres.xsd.nav.inntektsmelding_m._20181211.InntektsmeldingM

@JacksonXmlRootElement(namespace = "http://seres.no/xsd/NAV/Inntektsmelding_M/20181211", localName = "melding")
class Melding(val inntektsmeldingM: InntektsmeldingM)
