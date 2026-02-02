package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.hag.simba.utils.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.api.AvsenderSystem
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.utils.test.date.oktober
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID
import no.nav.hag.simba.utils.felles.domene.InntektsmeldingIntern as Inntektsmelding

class TilDokumenterKtTest {
    @Test
    fun tilDokumenter() {
        val mockInntektsmelding = mockInntektsmeldingV1()

        val dokumenter = tilDokumenter(mockInntektsmelding)

        assertEquals(1, dokumenter.size)
        assertEquals(2, dokumenter[0].dokumentVarianter.size)
        assertEquals("XML", dokumenter[0].dokumentVarianter[0].filtype)
        assertEquals("PDFA", dokumenter[0].dokumentVarianter[1].filtype)
        assertEquals("Inntektsmelding for sykepenger (endring) – 05.10.18–[…]–22.10.18", dokumenter[0].tittel)
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
        assertEquals("Inntektsmelding for sykepenger (endring) – 04.10.18–[…]–22.10.18", im.tilDokumentbeskrivelse())
    }

    @Test
    fun `tilDokumentbeskrivelse returnerer forventet med en periode`() {
        val im =
            mockInntekstmeldingMedPerioder(
                listOf(
                    1.oktober til 16.oktober,
                ),
            )
        assertEquals("Inntektsmelding for sykepenger (endring) – 01.10.18–16.10.18", im.tilDokumentbeskrivelse())
    }

    @Test
    fun `tilDokumentbeskrivelse returnerer forventet med ingen perioder`() {
        val im = mockInntekstmeldingMedPerioder(emptyList())
        assertEquals("Inntektsmelding for sykepenger (endring) – ingen AGP", im.tilDokumentbeskrivelse())
    }

    @Test
    fun `tilDokumentbeskrivelse returnerer det som er forventet for forskjellige typer`() {
        val id = UUID.randomUUID()
        val avsenderSystem = AvsenderSystem(Orgnr.genererGyldig(), "TestSys", "1.0")

        listOf(
            Inntektsmelding.Type.Selvbestemt(id) to null,
            Inntektsmelding.Type.Fisker(id) to ", fisker med hyre",
            Inntektsmelding.Type.UtenArbeidsforhold(id) to ", unntatt registrering i Aa-registeret",
            Inntektsmelding.Type.Behandlingsdager(id) to ", behandlingsdager",
            Inntektsmelding.Type.Forespurt(id, true) to null,
            Inntektsmelding.Type.Forespurt(id, false) to ", arbeidsgiverperiode – ikke forespurt",
            Inntektsmelding.Type.ForespurtEkstern(id, true, avsenderSystem) to null,
            Inntektsmelding.Type.ForespurtEkstern(id, false, avsenderSystem) to ", arbeidsgiverperiode – ikke forespurt",
        ).forEach { (imType, forventetTillegg) ->
            val beskrivelse = mockInntektsmeldingV1().copy(type = imType).tilDokumentbeskrivelse()
            val forventetBeskrivelse = "Inntektsmelding for sykepenger (endring${forventetTillegg.orEmpty()}) – 05.10.18–[…]–22.10.18"
            assertEquals(forventetBeskrivelse, beskrivelse)
        }
    }

    private fun mockInntekstmeldingMedPerioder(perioder: List<Periode>): Inntektsmelding =
        mockInntektsmeldingV1().copy(agp = mockInntektsmeldingV1().agp?.copy(perioder = perioder))
}
