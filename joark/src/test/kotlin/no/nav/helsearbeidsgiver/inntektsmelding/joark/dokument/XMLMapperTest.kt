package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.inntektsmelding.joark.mappers.InntektDokumentTilSkjemainnholdMapper
import no.seres.xsd.nav.inntektsmelding_m._20181211.InntektsmeldingM
import no.seres.xsd.nav.inntektsmelding_m._20181211.ObjectFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mapstruct.factory.Mappers

class XMLMapperTest {

    @Test
    fun `Bør generere xml fra InnsendingM dokument`() {
        val mockInntektsmeldingDokument = MockInntektsmeldingDokument()
        val mapper = Mappers.getMapper(InntektDokumentTilSkjemainnholdMapper::class.java)
        val inntektM = mapper.InntektDokumentTilInntekstmeldingM(mockInntektsmeldingDokument)
        val strIm = xmlMapper().writeValueAsString(inntektM)
        val of = ObjectFactory()
        val jaxbElement = of.createMelding(inntektM)
        // println(strIm)
        println(xmlMapper().writeValueAsString(jaxbElement))
        val reverseImM = xmlMapper().readValue(strIm, InntektsmeldingM::class.java)
        assertEquals(mockInntektsmeldingDokument.beregnetInntekt, reverseImM.skjemainnhold.arbeidsforhold.beregnetInntekt.beloep)
        assertEquals(
            mockInntektsmeldingDokument.arbeidsgiverperioder.size,
            reverseImM.skjemainnhold.sykepengerIArbeidsgiverperioden.arbeidsgiverperiodeListe.size
        )
    }
}
