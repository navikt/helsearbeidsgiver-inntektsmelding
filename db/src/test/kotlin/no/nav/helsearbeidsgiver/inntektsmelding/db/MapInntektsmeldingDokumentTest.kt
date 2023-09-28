package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.domene.inntektsmelding.Ferie
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Periode
import no.nav.helsearbeidsgiver.felles.test.mock.GYLDIG_INNSENDING_REQUEST
import no.nav.helsearbeidsgiver.utils.test.date.januar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class MapInntektsmeldingDokumentTest {

    @Test
    fun testmapInntektsmeldingDokument() {
        val fulltNavn = "Test Testesen"
        val arbeidsgiver = "Bedrift A/S"
        val innsenderNavn = "Hege fra HR"
        val inntektsmeldingDokument = mapInntektsmeldingDokument(GYLDIG_INNSENDING_REQUEST, fulltNavn, arbeidsgiver, innsenderNavn)
        assertNotNull(inntektsmeldingDokument.inntekt)
        assertEquals(GYLDIG_INNSENDING_REQUEST.inntekt.beregnetInntekt, inntektsmeldingDokument.inntekt?.beregnetInntekt)

        val periode = Periode(1.januar, 3.januar)
        val aarsak = Ferie(listOf(periode))
        val inntekt = Inntekt(true, 1.0, aarsak, true)

        val request2 = GYLDIG_INNSENDING_REQUEST.copy(inntekt = inntekt)
        val dok = mapInntektsmeldingDokument(request2, fulltNavn, arbeidsgiver, innsenderNavn)
        assertEquals(inntekt, dok.inntekt)
    }
}
