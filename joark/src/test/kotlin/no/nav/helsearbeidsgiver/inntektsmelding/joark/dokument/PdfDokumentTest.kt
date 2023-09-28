package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.domene.inntektsmelding.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Bonus
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Feilregistrert
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Ferie
import no.nav.helsearbeidsgiver.domene.inntektsmelding.FullLoennIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.InntektEndringAarsak
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.NyStilling
import no.nav.helsearbeidsgiver.domene.inntektsmelding.NyStillingsprosent
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Nyansatt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Permisjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Permittering
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Refusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.RefusjonEndring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Sykefravaer
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Tariffendring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.VarigLonnsendring
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmelding
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

class PdfDokumentTest {

    private val dag = LocalDate.of(2022, 12, 24)
    private val im = mockInntektsmelding()

    @Test
    fun `betaler full lønn i arbeidsgiverperioden`() {
        writePDF(
            "ikke_refusjon_og_full_lønn_i_arbeidsgiverperioden",
            im.copy(
                fullLønnIArbeidsgiverPerioden = FullLoennIArbeidsgiverPerioden(true),
                refusjon = Refusjon(false)
            )
        )
    }

    @Test
    fun `betaler ikke full lønn i arbeidsgiverperioden`() {
        writePDF(
            "ikke_refusjon_og_ikke_full_lønn_i_arbeidsgiverperioden",
            im.copy(
                fullLønnIArbeidsgiverPerioden = FullLoennIArbeidsgiverPerioden(
                    false,
                    BegrunnelseIngenEllerRedusertUtbetalingKode.PERMITTERING,
                    5000.0
                ),
                refusjon = Refusjon(false)
            )
        )
    }

    @Test
    fun `krever refusjon etter arbeidsgiverperioden`() {
        writePDF(
            "med_refusjon_og_full_lønn_arbeidsgiverperioden_opphører",
            im.copy(
                fullLønnIArbeidsgiverPerioden = FullLoennIArbeidsgiverPerioden(true),
                refusjon = Refusjon(
                    true,
                    25000.0,
                    dag.plusDays(3)
                )
            )
        )
    }

    @Test
    fun `med refusjon og uten endringer`() {
        writePDF(
            "med_refusjon_og_full_lønn_arbeidsgiverperioden_oppnører_ikke",
            im.copy(
                fullLønnIArbeidsgiverPerioden = FullLoennIArbeidsgiverPerioden(true),
                refusjon = Refusjon(
                    true,
                    25000.0,
                    null
                )
            )
        )
    }

    @Test
    fun `med refusjon med endringer`() {
        writePDF(
            "med_refusjon_og_endringer_i_beloep",
            im.copy(
                fullLønnIArbeidsgiverPerioden = FullLoennIArbeidsgiverPerioden(true),
                refusjon = Refusjon(
                    true,
                    25000.0,
                    null,
                    refusjonEndringer = listOf(
                        RefusjonEndring(140.0, dag.minusDays(4)),
                        RefusjonEndring(150.0, dag.minusDays(5)),
                        RefusjonEndring(160.0, dag.minusDays(6))
                    )
                )
            )
        )
    }

    @Test
    fun `med langt virksomhetsnavn over flere linjer`() {
        val imLangNavn = im.copy(
            virksomhetNavn = "Blå Rød Grønn Blåbærebærekraftsvennligutendørsbedrift AS"
        )
        val forventetInnhold = "Blå Rød Grønn${System.lineSeparator()}Blåbærebærekraftsvennligutendørsbedrift${System.lineSeparator()}AS"
        val pdfTekst = extractTextFromPdf(PdfDokument(imLangNavn).export())
        assert(pdfTekst!!.contains(forventetInnhold))
    }

    @Test
    fun `med langt fulltnavn over flere linjer`() {
        val imLangNavn = im.copy(
            fulltNavn = "Blå Rød Grønn BlåbærebærekraftsvennligutendørsNavn"
        )
        val forventetInnhold = "Blå Rød Grønn${System.lineSeparator()}BlåbærebærekraftsvennligutendørsNavn"
        val pdfDok = PdfDokument(imLangNavn).export()
        val pdfTekst = extractTextFromPdf(pdfDok)
        assert(pdfTekst!!.contains(forventetInnhold))
    }

    @Test
    fun `med kontaktInfo til arbeidsgiver`() {
        val tlf = "+4722555555"

        val medTelefon = im.copy(
            telefonnummer = tlf
        )
        writePDF(
            "med_begrunnelse",
            medTelefon
        )
        val pdfTekst = extractTextFromPdf(PdfDokument(medTelefon).export())
        assert(pdfTekst!!.contains(tlf.formaterTelefonnummer()))
        assert(pdfTekst.contains(im.innsenderNavn.toString()))
    }

    @Test
    fun `med begrunnelse`() {
        writePDF(
            "med_begrunnelse",
            im.copy(
                fullLønnIArbeidsgiverPerioden = FullLoennIArbeidsgiverPerioden(
                    false,
                    begrunnelse = BegrunnelseIngenEllerRedusertUtbetalingKode.FERIE_ELLER_AVSPASERING
                )
            )
        )
    }

    @Test
    fun `med inntekt endring årsak - alle varianter`() {
        val perioder = listOf(Periode(dag, dag.plusDays(12)), Periode(dag.plusDays(13), dag.plusDays(18)))
        val map = HashMap<String, InntektEndringAarsak>()
        map["tariffendring"] = Tariffendring(dag, dag.plusDays(2))
        map["ferie"] = Ferie(perioder)
        map["variglonnsendring"] = VarigLonnsendring(dag)
        map["nystilling"] = NyStilling(dag)
        map["nystillingsprosent"] = NyStillingsprosent(dag)
        map["bonus"] = Bonus()
        map["permisjon"] = Permisjon(perioder)
        map["permittering"] = Permittering(perioder)
        map["sykefravaer"] = Sykefravaer(perioder)
        map["nyansatt"] = Nyansatt
        map["feilregistrert"] = Feilregistrert

        map.forEach {
            writePDF(
                "med_inntekt_endring_${it.key}",
                im.copy(
                    inntekt = Inntekt(true, 123.0, it.value, true)
                )
            )
        }
        writePDF(
            "med_ingen_aarsak_inntekt_endring",
            im.copy(
                inntekt = Inntekt(true, 123.0, null, true)
            )
        )
    }
    private fun writePDF(title: String, im: Inntektsmelding) {
        // val file = File(System.getProperty("user.home"), "/Desktop/$title.pdf")
        val file = File.createTempFile(title, ".pdf")
        val writer = FileOutputStream(file)
        writer.write(PdfDokument(im).export())
        println("Lagde PDF $title med filnavn ${file.toPath()}")
    }

    // Hjelpemetode for å gjøre pdf til tekst for testing
    private fun extractTextFromPdf(pdf: ByteArray): String? {
        val pdfReader = PDDocument.load(pdf)
        val pdfStripper = PDFTextStripper()
        val allTextInDocument = pdfStripper.getText(pdfReader)
        pdfReader.close()
        return allTextInDocument
    }
}
