package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import io.kotest.equals.Equality
import io.kotest.equals.byReflectionUsingFields
import no.nav.helsearbeidsgiver.inntektsmelding.joark.mappers.InntektDokumentTilSkjemainnholdMapper
import no.seres.xsd.nav.inntektsmelding_m._20181211.InntektsmeldingM
import org.apache.commons.lang3.builder.EqualsBuilder
import org.junit.jupiter.api.Test
import org.mapstruct.factory.Mappers
import kotlin.reflect.full.memberProperties

class XMLMapperTest {

    @Test
    fun `Bør generere xml fra InnsendingM dokument`() {
        val mockInntektsmeldingDokument = MockInntektsmeldingDokument()
        val mapper = Mappers.getMapper(InntektDokumentTilSkjemainnholdMapper::class.java)
        val inntektM = mapper.InntektDokumentTilInntekstmeldingM(mockInntektsmeldingDokument)

        val strIm = xmlMapper().writeValueAsString(inntektM)
        println(strIm)
        val reverseImM = xmlMapper().readValue(strIm, InntektsmeldingM::class.java)
       // Assertions.assertEquals(inntektM, reverseImM)
        EqualsBuilder().setTestRecursive(true).reflectionAppend(inntektM, reverseImM)
            val result = Equality.byReflectionUsingFields<InntektsmeldingM>(*InntektsmeldingM::class.memberProperties.toTypedArray()).verify(inntektM,reverseImM)
        println(result)

    }
}
