package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.utils.test.date.oktober
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TilDokumenterKtTest {
    @Test
    fun tilDokumenter() {
        val mockInntektsmelding = mockInntektsmeldingV1()

        val dokumenter = tilDokumenter(mockInntektsmelding)

        assertEquals(1, dokumenter.size)
        assertEquals(2, dokumenter[0].dokumentVarianter.size)
        assertEquals("XML", dokumenter[0].dokumentVarianter[0].filtype)
        assertEquals("PDFA", dokumenter[0].dokumentVarianter[1].filtype)
        assertEquals("Inntektsmelding-05.10.2018 - [...] - 22.10.2018", dokumenter[0].tittel)
    }

    @Test
    fun `tilDokumentbeskrivelse returnerer forventet med to perioder`() {
        val im =
            mockInntekstmeldingMedPerioder(
                listOf(
                    4.oktober til 14.oktober,
                    20.oktober til 22.oktober,
                ),
            )
        assertEquals("Inntektsmelding-04.10.2018 - [...] - 22.10.2018", im.tilDokumentbeskrivelse())
    }

    @Test
    fun `tilDokumentbeskrivelse returnerer forventet med en periode`() {
        val im =
            mockInntekstmeldingMedPerioder(
                listOf(
                    1.oktober til 16.oktober,
                ),
            )
        assertEquals("Inntektsmelding-01.10.2018 - 16.10.2018", im.tilDokumentbeskrivelse())
    }

    @Test
    fun `tilDokumentbeskrivelse returnerer forventet med ingen perioder`() {
        val im = mockInntekstmeldingMedPerioder(emptyList())
        assertEquals("Inntektsmelding-(ingen agp)", im.tilDokumentbeskrivelse())
    }

    @Test
    fun `tilDokumentbeskrivelse returnerer det som er forventet for forskjellige typer`() {
        val im = mockInntektsmeldingV1()
        val id = im.type.id
        val standardBeskrivelse = "Inntektsmelding-05.10.2018 - [...] - 22.10.2018"

        setOf(
            im.copy(type = Inntektsmelding.Type.Fisker(id)) to "$standardBeskrivelse (Fisker m/hyre)",
            im.copy(type = Inntektsmelding.Type.UtenArbeidsforhold(id)) to "$standardBeskrivelse (Uten arbeidsforhold)",
            im.copy(type = Inntektsmelding.Type.Forespurt(id)) to standardBeskrivelse,
            im.copy(type = Inntektsmelding.Type.Selvbestemt(id)) to standardBeskrivelse,
        ).forEach { (im, forventet) ->
            assertEquals(forventet, im.tilDokumentbeskrivelse())
        }
    }

    private fun mockInntekstmeldingMedPerioder(perioder: List<Periode>): Inntektsmelding =
        mockInntektsmeldingV1().copy(agp = mockInntektsmeldingV1().agp?.copy(perioder = perioder))
}
