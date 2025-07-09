package no.nav.helsearbeidsgiver.inntektsmelding.joark

import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding.Type
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
    fun `tilDokumentbeskrivelse returnerer forventet for forskjellige perioder`() {
        val im = mockInntektsmeldingV1()
        val perioder = listOf(4.oktober til 14.oktober, 20.oktober til 22.oktober)

        setOf(
            perioder.take(0) to "Inntektsmelding-(ingen agp)",
            perioder.take(1) to "Inntektsmelding-04.10.2018 - 14.10.2018",
            perioder.take(2) to "Inntektsmelding-04.10.2018 - [...] - 22.10.2018",
        ).forEach { (perioder, forventet) ->
            im.medAgpPerioder(perioder).tilDokumentbeskrivelse() shouldBe forventet
        }
    }

    @Test
    fun `tilDokumentbeskrivelse returnerer forventet for forskjellige typer`() {
        val im = mockInntektsmeldingV1()
        val id = im.type.id
        val standardBeskrivelse = "Inntektsmelding-05.10.2018 - [...] - 22.10.2018"

        setOf(
            im.medType(Type.Forespurt(id)) to standardBeskrivelse,
            im.medType(Type.Selvbestemt(id)) to standardBeskrivelse,
            im.medType(Type.Fisker(id)) to "$standardBeskrivelse (Fisker m/hyre)",
            im.medType(Type.UtenArbeidsforhold(id)) to "$standardBeskrivelse (Uten arbeidsforhold)",
        ).forEach { (im, forventet) ->
            im.tilDokumentbeskrivelse() shouldBe forventet
        }
    }

    private fun Inntektsmelding.medType(type: Type): Inntektsmelding =
        this.copy(type = type)

    private fun Inntektsmelding.medAgpPerioder(perioder: List<Periode>): Inntektsmelding =
        this.copy(agp = this.agp?.copy(perioder = perioder))
}
