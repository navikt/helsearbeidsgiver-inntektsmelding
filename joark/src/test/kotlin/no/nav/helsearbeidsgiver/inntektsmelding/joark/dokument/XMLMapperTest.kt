package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import io.kotest.equals.Equality
import io.kotest.equals.byReflectionUsingFields
import no.nav.helsearbeidsgiver.inntektsmelding.joark.mappers.InntektDokumentTilSkjemainnholdMapper
import no.seres.xsd.nav.inntektsmelding_m._20181211.InntektsmeldingM
import no.seres.xsd.nav.inntektsmelding_m._20181211.ObjectFactory
import org.apache.commons.lang3.builder.EqualsBuilder
import org.junit.jupiter.api.Test
import org.mapstruct.factory.Mappers
import java.io.StringWriter
import javax.xml.bind.JAXBContext
import kotlin.reflect.full.memberProperties

class XMLMapperTest {

    @Test
    fun `Bør generere xml fra InnsendingM dokument`() {
        val mockInntektsmeldingDokument = MockInntektsmeldingDokument()
        val mapper = Mappers.getMapper(InntektDokumentTilSkjemainnholdMapper::class.java)
        val inntektM = mapper.InntektDokumentTilInntekstmeldingM(mockInntektsmeldingDokument)
        val ctx = JAXBContext.newInstance(ObjectFactory::class.java)
        val writer = StringWriter()
        ctx.createMarshaller().marshal(ObjectFactory().createMelding(inntektM),writer)
        println(writer.toString())
        val strIm = xmlMapper().writeValueAsString(Melding(inntektM))
        println(strIm)
        val reverseImM = xmlMapper().readValue(strIm, InntektsmeldingM::class.java)
        EqualsBuilder().setExcludeFields("serialVersionUID").reflectionAppend(
            inntektM.skjemainnhold.sykepengerIArbeidsgiverperioden.arbeidsgiverperiodeListe,
            reverseImM.skjemainnhold.sykepengerIArbeidsgiverperioden.arbeidsgiverperiodeListe
        )
        EqualsBuilder().setTestRecursive(true).reflectionAppend(inntektM, reverseImM)
        val result = Equality.byReflectionUsingFields<InntektsmeldingM>(*InntektsmeldingM::class.memberProperties.toTypedArray()).verify(
            inntektM,
            reverseImM
        )
        println(result)
    }
}
