package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.hag.simba.utils.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.api.AvsenderSystem
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.utils.test.date.oktober
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

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
        val id = UUID.randomUUID()
        val avsenderSystem = AvsenderSystem(Orgnr.genererGyldig(), "TestSys", "1.0")

        listOf(
            Inntektsmelding.Type.Selvbestemt(id) to null,
            Inntektsmelding.Type.Fisker(id) to " (Fisker m/hyre)",
            Inntektsmelding.Type.UtenArbeidsforhold(id) to " (Unntatt registrering i Aa-registeret)",
            Inntektsmelding.Type.Behandlingsdager(id) to " (Behandlingsdager)",
            Inntektsmelding.Type.Forespurt(id, true) to null,
            Inntektsmelding.Type.Forespurt(id, false) to " (Arbeidsgiverperiode – ikke forespurt)",
            Inntektsmelding.Type.ForespurtEkstern(id, true, avsenderSystem) to null,
            Inntektsmelding.Type.ForespurtEkstern(id, false, avsenderSystem) to " (Arbeidsgiverperiode – ikke forespurt)",
        ).forEach { (imType, forventetTillegg) ->
            val beskrivelse = mockInntektsmeldingV1().copy(type = imType).tilDokumentbeskrivelse()
            val forventetBeskrivelse = "Inntektsmelding-05.10.2018 - [...] - 22.10.2018" + forventetTillegg.orEmpty()
            assertEquals(forventetBeskrivelse, beskrivelse)
        }
    }

    private fun mockInntekstmeldingMedPerioder(perioder: List<Periode>): Inntektsmelding =
        mockInntektsmeldingV1().copy(agp = mockInntektsmeldingV1().agp?.copy(perioder = perioder))
}
