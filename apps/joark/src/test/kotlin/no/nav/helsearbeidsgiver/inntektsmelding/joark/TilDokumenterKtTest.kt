package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.utils.test.date.oktober
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class TilDokumenterKtTest {
    @Test
    fun tilDokumenter() {
        val mockInntektsmelding = mockInntektsmeldingV1()

        val dokumenter = tilDokumenter(UUID.randomUUID(), mockInntektsmelding)

        assertEquals(1, dokumenter.size)
        assertEquals(2, dokumenter[0].dokumentVarianter.size)
        assertEquals("XML", dokumenter[0].dokumentVarianter[0].filtype)
        assertEquals("PDFA", dokumenter[0].dokumentVarianter[1].filtype)
    }

    @Test
    fun tittelFormateresRiktig() {
        val mockInntektsmelding = mockInntektsmeldingV1()

        val dokumenter = tilDokumenter(UUID.randomUUID(), mockInntektsmelding)

        val orgnr = mockInntektsmelding.avsender.orgnr.verdi

        assertEquals("Inntektsmelding-$orgnr-05.10.2018 - [...] - 22.10.2018", dokumenter[0].tittel)
    }

    fun mockInntekstmeldingMedPerioder(perioder: List<Periode>): Inntektsmelding =
        mockInntektsmeldingV1().copy(agp = mockInntektsmeldingV1().agp?.copy(perioder = perioder))

    @Test
    fun journalTittelToPerioder() {
        val im =
            mockInntekstmeldingMedPerioder(
                listOf(
                    5.oktober til 15.oktober,
                    20.oktober til 22.oktober,
                ),
            )
        assertEquals("Inntektsmelding-${im.avsender.orgnr.verdi}-05.10.2018 - [...] - 22.10.2018", im.tilJournalTittel())
    }

    @Test
    fun journalTittelEnPerioder() {
        val im =
            mockInntekstmeldingMedPerioder(
                listOf(
                    1.oktober til 16.oktober,
                ),
            )
        assertEquals("Inntektsmelding-${im.avsender.orgnr.verdi}-01.10.2018 - 16.10.2018", im.tilJournalTittel())
    }

    @Test
    fun journalTittelIngenPerioder() {
        val im = mockInntekstmeldingMedPerioder(emptyList())
        assertEquals("Inntektsmelding-${im.avsender.orgnr.verdi}- (ingen agp)", im.tilJournalTittel())
    }
}
