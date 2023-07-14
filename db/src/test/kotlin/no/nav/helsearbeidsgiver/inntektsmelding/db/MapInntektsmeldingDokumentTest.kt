package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Ferie
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Inntekt
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode
import no.nav.helsearbeidsgiver.felles.test.mock.GYLDIG_INNSENDING_REQUEST
import no.nav.helsearbeidsgiver.utils.test.date.januar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MapInntektsmeldingDokumentTest {

    @Test
    fun testmapInntektsmeldingDokument() {
        val fulltNavn = "Test Testesen"
        val arbeidsgiver = "Bedrift A/S"
        val inntektsmeldingDokument = mapInntektsmeldingDokument(GYLDIG_INNSENDING_REQUEST, fulltNavn, arbeidsgiver)
        assertNotNull(inntektsmeldingDokument.inntekt)
        assertEquals(GYLDIG_INNSENDING_REQUEST.inntekt.beregnetInntekt, inntektsmeldingDokument.inntekt?.beregnetInntekt)

        val periode = Periode(1.januar, 3.januar)
        val aarsak = Ferie(listOf(periode))
        val inntekt = Inntekt(true, BigDecimal.ONE, aarsak, true)

        val request2 = GYLDIG_INNSENDING_REQUEST.copy(inntekt = inntekt)
        val dok = mapInntektsmeldingDokument(request2, fulltNavn, arbeidsgiver)
        assertEquals(inntekt, dok.inntekt)
    }
}
