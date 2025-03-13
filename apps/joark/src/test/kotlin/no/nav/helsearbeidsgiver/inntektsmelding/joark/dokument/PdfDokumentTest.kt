package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Bonus
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Feilregistrert
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Ferie
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Ferietrekk
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.InntektEndringAarsak
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.NyStilling
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.NyStillingsprosent
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Nyansatt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Permisjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Permittering
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.RedusertLoennIAgp
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Refusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.RefusjonEndring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Sykefravaer
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Tariffendring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.VarigLoennsendring
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

class PdfDokumentTest {
    private val dag = LocalDate.of(2022, 12, 24)
    private val im = mockInntektsmeldingV1()
    private val endringAarsaker = endringAarsakMap().values.toList()

    @Test
    fun `betaler full lønn i arbeidsgiverperioden`() {
        writePDF(
            "ikke_refusjon_og_full_lønn_i_arbeidsgiverperioden",
            im.copy(
                agp =
                    im.agp.shouldNotBeNull().copy(
                        redusertLoennIAgp = null,
                    ),
                refusjon = null,
            ),
        )
    }

    @Test
    fun `betaler ikke full lønn i arbeidsgiverperioden`() {
        writePDF(
            "ikke_refusjon_og_ikke_full_lønn_i_arbeidsgiverperioden",
            im.copy(
                agp =
                    im.agp.shouldNotBeNull().copy(
                        redusertLoennIAgp =
                            RedusertLoennIAgp(
                                beloep = 5000.0,
                                begrunnelse = RedusertLoennIAgp.Begrunnelse.Permittering,
                            ),
                    ),
                refusjon = null,
            ),
        )
    }

    @Test
    fun `krever refusjon etter arbeidsgiverperioden`() {
        writePDF(
            "med_refusjon_og_full_lønn_arbeidsgiverperioden_opphører",
            im.copy(
                agp =
                    im.agp.shouldNotBeNull().copy(
                        redusertLoennIAgp = null,
                    ),
                refusjon =
                    Refusjon(
                        beloepPerMaaned = 25000.0,
                        endringer = emptyList(),
                        sluttdato = dag.plusDays(3),
                    ),
            ),
        )
    }

    @Test
    fun `med refusjon og uten endringer`() {
        writePDF(
            "med_refusjon_og_full_lønn_arbeidsgiverperioden_oppnører_ikke",
            im.copy(
                agp =
                    im.agp.shouldNotBeNull().copy(
                        redusertLoennIAgp = null,
                    ),
                refusjon =
                    Refusjon(
                        beloepPerMaaned = 25000.0,
                        endringer = emptyList(),
                        sluttdato = null,
                    ),
            ),
        )
    }

    @Test
    fun `med refusjon med endringer`() {
        writePDF(
            "med_refusjon_og_endringer_i_beloep",
            im.copy(
                agp =
                    im.agp.shouldNotBeNull().copy(
                        redusertLoennIAgp = null,
                    ),
                refusjon =
                    Refusjon(
                        beloepPerMaaned = 25000.0,
                        endringer =
                            listOf(
                                RefusjonEndring(140.0, dag.minusDays(4)),
                                RefusjonEndring(150.0, dag.minusDays(5)),
                                RefusjonEndring(160.0, dag.minusDays(6)),
                            ),
                        sluttdato = null,
                    ),
            ),
        )
    }

    @Test
    fun `med langt virksomhetsnavn over flere linjer`() {
        val imLangNavn =
            im.copy(
                avsender =
                    im.avsender.copy(
                        orgNavn = "Blå Rød Grønn Blåbærebærekraftsvennligutendørsbedrift AS",
                    ),
            )
        val forventetInnhold = "Blå Rød Grønn${System.lineSeparator()}Blåbærebærekraftsvennligutendørsbedrift${System.lineSeparator()}AS"
        val pdfTekst = extractTextFromPdf(PdfDokument(imLangNavn).export())
        writePDF("med langt virksomhetsnavn over flere linjer", imLangNavn)
        assert(pdfTekst!!.contains(forventetInnhold))
    }

    @Test
    fun `med langt navn over flere linjer`() {
        val imLangNavn =
            im.copy(
                sykmeldt =
                    im.sykmeldt.copy(
                        navn = "Pippilotta Viktualia Rullegardina Krusemynte Efraimsdatter Langstrømpe",
                    ),
            )
        val forventetInnhold = "Pippilotta Viktualia Rullegardina${System.lineSeparator()}Krusemynte Efraimsdatter Langstrømpe"
        val pdfTekst = extractTextFromPdf(PdfDokument(imLangNavn).export())
        writePDF("med langt navn over flere linjer", imLangNavn)
        assert(pdfTekst!!.contains(forventetInnhold))
    }

    @Test
    fun `med langt innsendernavn med store bokstaver over flere linjer`() {
        val imLangNavn =
            im.copy(
                avsender =
                    im.avsender.copy(
                        navn = "ANNASENDER CAPSLOCKUMSEN TEKSTBREKKSON",
                    ),
            )
        val forventetInnhold = "ANNASENDER CAPSLOCKUMSEN${System.lineSeparator()}TEKSTBREKKSON"
        val pdfTekst = extractTextFromPdf(PdfDokument(imLangNavn).export())
        writePDF("med langt innsendernavn med store bokstaver over flere linjer", imLangNavn)
        assert(pdfTekst!!.contains(forventetInnhold))
    }

    @Test
    fun `med langt fulltnavn over flere linjer`() {
        val imLangNavn =
            im.copy(
                sykmeldt =
                    im.sykmeldt.copy(
                        navn = "Blå Rød Grønn BlåbærebærekraftsvennligutendørsNavn",
                    ),
            )
        val forventetInnhold = "Blå Rød Grønn${System.lineSeparator()}BlåbærebærekraftsvennligutendørsNavn"
        val pdfDok = PdfDokument(imLangNavn).export()
        val pdfTekst = extractTextFromPdf(pdfDok)
        assert(pdfTekst!!.contains(forventetInnhold))
    }

    @Test
    fun `med kontaktInfo til arbeidsgiver`() {
        val tlf = "+4722555555"

        val medTelefon =
            im.copy(
                avsender =
                    im.avsender.copy(
                        tlf = tlf,
                    ),
            )
        writePDF(
            "med_begrunnelse",
            medTelefon,
        )
        val pdfTekst = extractTextFromPdf(PdfDokument(medTelefon).export())
        assert(pdfTekst!!.contains(tlf.formaterTelefonnummer()))
        assert(pdfTekst.contains(im.avsender.navn))
    }

    @Test
    fun `med begrunnelse`() {
        writePDF(
            "med_begrunnelse",
            im.copy(
                agp =
                    im.agp.shouldNotBeNull().copy(
                        redusertLoennIAgp =
                            RedusertLoennIAgp(
                                beloep = 0.0,
                                begrunnelse = RedusertLoennIAgp.Begrunnelse.FerieEllerAvspasering,
                            ),
                    ),
            ),
        )
    }

    @Test
    fun `med ingen arbeidsgiverperiode`() {
        val medAgp = im
        val agpErNull = im.copy(agp = null)
        val agpHarTomPeriodeListe = im.copy(agp = im.agp.shouldNotBeNull().copy(perioder = emptyList()))

        mapOf(
            "med_arbeidsgiverperiode" to medAgp,
            "med_arbeidsgiverperiode_lik_null" to agpErNull,
            "med_ingen_arbeidsgiver_perioder" to agpHarTomPeriodeListe,
        ).forEach { (filNavn, im) ->
            writePDF(filNavn, im)
        }

        val pdfAgpRelevantTekst = "Betaler arbeidsgiver full lønn til arbeidstaker i arbeidsgiverperioden?"

        pdfTekstFraIm(medAgp) shouldContain pdfAgpRelevantTekst
        pdfTekstFraIm(agpErNull) shouldNotContain pdfAgpRelevantTekst
        pdfTekstFraIm(agpHarTomPeriodeListe) shouldNotContain pdfAgpRelevantTekst
    }

    @Test
    fun `med inntekt endring årsak - alle varianter`() {
        val map = endringAarsakMap()

        map.forEach {
            writePDF(
                "med_inntekt_endring_${it.key}",
                im.copy(
                    inntekt =
                        im.inntekt.shouldNotBeNull().copy(
                            beloep = 123.0,
                            endringAarsak = it.value,
                            endringAarsaker = listOf(it.value),
                        ),
                ),
            )
        }
        writePDF(
            "med_ingen_aarsak_inntekt_endring",
            im.copy(
                inntekt =
                    im.inntekt.shouldNotBeNull().copy(
                        beloep = 123.0,
                        endringAarsak = null,
                        endringAarsaker = emptyList(),
                    ),
            ),
        )
    }

    @Test
    fun `med en begrunnelse blir teksten lagt til`() {
        endringAarsaker.map { listOf(it) }.map { it.tilIm() }.forEach { im ->
            im.inntekt?.endringAarsaker?.forEach { endring ->
                pdfTekstFraIm(im) shouldContain "Endringsårsak\n${endring.beskrivelse()}"
            }
        }
    }

    @Test
    fun `med 2, 3, 4, 5 eller 6 begrunnelser blir teksten lagt til`() {
        (2..6).forEach { n ->
            endringAarsaker
                .windowed(n, 1, partialWindows = false)
                .map { it.tilIm() }
                .forEach { im ->
                    im.inntekt?.endringAarsaker?.forEachIndexed { indeks, endring ->
                        val tekst = pdfTekstFraIm(im)
                        tekst shouldContain "Endringsårsak (${indeks + 1} av $n)"
                        tekst shouldContain endring.beskrivelse()
                    }
                }
        }
    }

    private fun List<InntektEndringAarsak>.tilIm(): Inntektsmelding =
        im.copy(
            inntekt =
                im.inntekt.shouldNotBeNull().copy(
                    beloep = 123.0,
                    endringAarsaker = this,
                    endringAarsak = null,
                ),
        )

    private fun pdfTekstFraIm(im: Inntektsmelding): String {
        val pdfDok = PdfDokument(im).export()
        val pdfTekst = extractTextFromPdf(pdfDok)
        return pdfTekst.shouldNotBeNull()
    }

    private fun writePDF(
        title: String,
        im: Inntektsmelding,
    ) {
//        val file = File(System.getProperty("user.home"), "/Desktop/pdf/$title.pdf")
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

    private fun endringAarsakMap(): HashMap<String, InntektEndringAarsak> {
        val perioder = listOf(Periode(dag, dag.plusDays(12)), Periode(dag.plusDays(13), dag.plusDays(18)))
        val map = HashMap<String, InntektEndringAarsak>()
        map["bonus"] = Bonus
        map["feilregistrert"] = Feilregistrert
        map["ferie"] = Ferie(perioder)
        map["ferietrekk"] = Ferietrekk
        map["nyansatt"] = Nyansatt
        map["nystilling"] = NyStilling(dag)
        map["nystillingsprosent"] = NyStillingsprosent(dag)
        map["permisjon"] = Permisjon(perioder)
        map["permittering"] = Permittering(perioder)
        map["sykefravaer"] = Sykefravaer(perioder)
        map["tariffendring"] = Tariffendring(dag, dag.plusDays(2))
        map["variglonnsendring"] = VarigLoennsendring(dag)
        return map
    }
}
